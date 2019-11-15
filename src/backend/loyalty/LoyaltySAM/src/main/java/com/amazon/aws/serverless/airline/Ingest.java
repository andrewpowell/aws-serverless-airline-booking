package com.amazon.aws.serverless.airline;

import com.amazon.ion.IonValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaRuntime;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SNSEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import software.amazon.qldb.QldbSession;
import software.amazon.qldb.Result;
import software.amazon.qldb.Transaction;
import software.amazon.qldb.TransactionExecutor;

import java.text.SimpleDateFormat;
import java.util.*;

public class Ingest implements RequestHandler<SNSEvent, IngestResult> {
    private static final Gson gson = new GsonBuilder().create();

    private static void addPoints(final String customerId, final int points) {
        final String query = "SELECT l FROM " + Constants.LOYALTY_POINTS_TABLE_NAME + " AS l WHERE l.CustomerId = ? ";
        final String tableName = Constants.LOYALTY_POINTS_TABLE_NAME;
        SimpleDateFormat sdf;
        sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");
        sdf.setTimeZone(TimeZone.getDefault());
        String isoDate = sdf.format(new Date());
        LoyaltyRecord item = new LoyaltyRecord();

        try (final QldbSession session = ConnectToLedger.createQldbDriver().getSession()) {
            try (final Transaction transaction = session.startTransaction()) {
                final TransactionExecutor txn = new TransactionExecutor(transaction);
                final List<IonValue> parameters = Collections.singletonList(Constants.MAPPER.writeValueAsIonValue(customerId));
                final Result result = txn.execute(query, parameters);
                List<LoyaltyRecord> records = new ArrayList<>();

                result.forEach(resultItem -> {
                    String rawJson = resultItem.toPrettyString();
                    records.add(gson.fromJson(rawJson, LoyaltyRecord.class));
                });

                if (records.size() == 1) {
                    item = records.get(0);
                }
            }
        } catch (Exception e) {
            LambdaRuntime.getLogger().log("ERROR: " + e.getLocalizedMessage());
        }

        try (final QldbSession session = ConnectToLedger.createQldbDriver().getSession()) {
            try (final Transaction transaction = session.startTransaction()) {
                final TransactionExecutor txn = new TransactionExecutor(transaction);
                if (item.getId() == null || item.getId().equals("")) {
                    item.setId(UUID.randomUUID().toString());
                    item.setCustomerId(customerId);
                    item.setDate(isoDate);
                    item.setFlag(LoyaltyStatus.ACTIVE.toString());
                    item.setPoints(points);
                    final String insertQuery = "INSERT INTO " + Constants.LOYALTY_POINTS_TABLE_NAME + " VALUE ?";
                    final IonValue ionDocuments = Constants.MAPPER.writeValueAsIonValue(item);
                    final List<IonValue> parameters = Collections.singletonList(ionDocuments);
                    LambdaRuntime.getLogger().log("INSERTING: " + gson.toJson(item));
                    txn.execute(insertQuery, parameters);
                } else {
                    item.setDate(isoDate);
                    item.setFlag(LoyaltyStatus.ACTIVE.toString());
                    item.setPoints(points);
                    final String updateQuery = "UPDATE " + Constants.LOYALTY_POINTS_TABLE_NAME + " AS v SET v.Points = ?, v.Flag = ?, v.Date = ? WHERE v.Id = ?";
                    final List<IonValue> parameters = new ArrayList<>();
                    parameters.add(Constants.MAPPER.writeValueAsIonValue(item.getPoints()));
                    parameters.add(Constants.MAPPER.writeValueAsIonValue(item.getFlag()));
                    parameters.add(Constants.MAPPER.writeValueAsIonValue(item.getDate()));
                    parameters.add(Constants.MAPPER.writeValueAsIonValue(item.getId()));
                    txn.execute(updateQuery, parameters);
                }
            }
        } catch (Exception e) {
            LambdaRuntime.getLogger().log("ERROR: " + e.getLocalizedMessage());
        }

    }

    @Override
    public IngestResult handleRequest(SNSEvent event, Context context) {
        try {
            String rawMessage = event.getRecords().get(0).getSNS().getMessage();
            IngestData data = gson.fromJson(rawMessage, IngestData.class);
            String customerId = data.getCustomerId();
            int points = data.getPrice();
            if (points == 0) {
                return new IngestResult("Points cannot be undefined or falsy");
            }
            addPoints(customerId, points);
        } catch (Exception error) {
            context.getLogger().log("ERROR: " + error.getLocalizedMessage());
        }
        return new IngestResult("OK");
    }

}
