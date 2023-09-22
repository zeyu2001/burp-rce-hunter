package rcehunter;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.collaborator.CollaboratorClient;
import burp.api.montoya.collaborator.SecretKey;
import burp.api.montoya.persistence.PersistedObject;

import rcehunter.poller.Poller;

import java.time.Duration;

public class RceHunter implements BurpExtension {
    private MontoyaApi api;

    @Override
    public void initialize(MontoyaApi api) {
        this.api = api;

        api.extension().setName("RCE Hunter");

        CollaboratorClient collaboratorClient = createCollaboratorClient(api.persistence().extensionData());

        // Log any stored interactions.
        InteractionLogger interactionLogger = new InteractionLogger(api);
        interactionLogger.logInteractions(collaboratorClient.getAllInteractions());

        api.proxy().registerRequestHandler(new MyProxyRequestHandler(this.api, collaboratorClient));

        // Periodically poll the CollaboratorClient to retrieve any new interactions.
        Poller collaboratorPoller = new Poller(collaboratorClient, Duration.ofSeconds(10));
        collaboratorPoller.registerInteractionHandler(new MyInteractionHandler(api, interactionLogger));
        collaboratorPoller.start();

        api.extension().registerUnloadingHandler(() ->
        {
            // Stop polling the CollaboratorClient.
            collaboratorPoller.shutdown();

            api.logging().logToOutput("Extension unloading...");
        });
    }

    private CollaboratorClient createCollaboratorClient(PersistedObject persistedData)
    {
        CollaboratorClient collaboratorClient;

        String existingCollaboratorKey = persistedData.getString("persisted_collaborator");

        if (existingCollaboratorKey != null)
        {
            api.logging().logToOutput("Creating Collaborator client from key.");
            collaboratorClient = api.collaborator().restoreClient(SecretKey.secretKey(existingCollaboratorKey));
        }
        else
        {
            api.logging().logToOutput("No previously found Collaborator client. Creating new client...");
            collaboratorClient = api.collaborator().createClient();

            // Save the secret key of the CollaboratorClient so that you can retrieve it later.
            api.logging().logToOutput("Saving Collaborator secret key.");
            persistedData.setString("persisted_collaborator", collaboratorClient.getSecretKey().toString());
        }

        return collaboratorClient;
    }
}