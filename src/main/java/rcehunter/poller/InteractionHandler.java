package rcehunter.poller;

import burp.api.montoya.collaborator.Interaction;

public interface InteractionHandler
{
    void handleInteraction(Interaction interaction);
}