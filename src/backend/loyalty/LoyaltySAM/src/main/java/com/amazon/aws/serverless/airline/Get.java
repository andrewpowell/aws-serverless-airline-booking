package com.amazon.aws.serverless.airline;

import com.amazon.ion.IonStruct;
import com.amazon.ion.IonValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.LambdaRuntime;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2ProxyRequestEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import software.amazon.qldb.QldbSession;
import software.amazon.qldb.Result;
import software.amazon.qldb.Transaction;
import software.amazon.qldb.TransactionExecutor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handler for requests to Lambda function.
 */
public class Get implements RequestHandler<APIGatewayV2ProxyRequestEvent, GatewayResponse> {

    public static final LambdaLogger log = LambdaRuntime.getLogger();
    private static final Gson gson = new GsonBuilder().create();
    private static final String query = "SELECT * FROM " + Constants.LOYALTY_POINTS_TABLE_NAME + " l WHERE l.customerId = ? ";

    static LoyaltyTier level(int points) {
        if (points >= LoyaltyTierPoints.PLATINUM.value())
            return LoyaltyTier.PLATINUM;
        else if (points >= LoyaltyTierPoints.GOLD.value())
            return LoyaltyTier.GOLD;
        else if (points >= LoyaltyTierPoints.SILVER.value())
            return LoyaltyTier.SILVER;
        else
            return LoyaltyTier.BRONZE;
    }

    static int nextTier(int points, LoyaltyTier level) {
        if (level.equals(LoyaltyTier.BRONZE))
            return LoyaltyTierPoints.SILVER.value() - points;
        else if (level.equals(LoyaltyTier.SILVER))
            return LoyaltyTierPoints.GOLD.value() - points;
        else if (level.equals(LoyaltyTier.GOLD))
            return LoyaltyTierPoints.PLATINUM.value() - points;
        else
            return 0;
    }

    public static List<IonStruct> toIonStructs(final Result result) {
        final List<IonStruct> documentList = new ArrayList<>();
        result.iterator().forEachRemaining(row -> documentList.add((IonStruct) row));
        return documentList;
    }

    private static int createTable(final TransactionExecutor txn, final String tableName) {
        final String createTable = String.format("CREATE TABLE %s", tableName);
        final Result result = txn.execute(createTable);
        return toIonValues(result).size();
    }

    private static List<IonValue> toIonValues(Result result) {
        final List<IonValue> valueList = new ArrayList<>();
        result.iterator().forEachRemaining(valueList::add);
        return valueList;
    }

    public GatewayResponse handleRequest(final APIGatewayV2ProxyRequestEvent event, final Context context) {
        if (event.getPathParameters() == null || event.getPathParameters().get("customerId") == null) {
            return new GatewayResponse("customerId not defined", event.getHeaders(), 500);
        }

        final String tableName = Constants.LOYALTY_POINTS_TABLE_NAME;
        final String customerId = event.getPathParameters().get("customerId");

        try (QldbSession qldbSession = ConnectToLedger.createQldbSession()) {
            qldbSession.execute(txn -> {
                createTable(txn, tableName);
            }, (retryAttempt) -> log.log("Retrying due to OCC conflict..."));
        } catch (Exception e) {
            log.log("Tables already exist.");
        }

        try (final QldbSession session = ConnectToLedger.createQldbDriver().getSession()) {
            try (final Transaction transaction = session.startTransaction()) {
                final TransactionExecutor txn = new TransactionExecutor(transaction);

                final List<IonValue> parameters = Collections.singletonList(Constants.MAPPER.writeValueAsIonValue(customerId));
                final Result result = txn.execute(query, parameters);

                List<LoyaltyRecord> records = new ArrayList<>();

                //List<IonStruct> structList = toIonStructs(result);

                result.forEach(item -> {
                    String rawJson = item.toPrettyString();
                    records.add(gson.fromJson(rawJson, LoyaltyRecord.class));
                });

                if (records.size() == 1) {
                    LoyaltyRecord record = records.get(0);
                    return new GatewayResponse(gson.toJson(record.toResult()), event.getHeaders(), 200);
                } else {
                    return new GatewayResponse("", event.getHeaders(), 404);
                }
            }
        } catch (Exception e) {
            return new GatewayResponse(e.getLocalizedMessage(), event.getHeaders(), 500);
        }


    }
}
