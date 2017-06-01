//
// Wire
// Copyright (C) 2016 Wire Swiss GmbH
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/.
//

package com.wire.bots.jira;

import com.wire.bots.jira.utils.SessionIdentifierGenerator;
import com.wire.bots.sdk.Logger;
import com.wire.bots.sdk.MessageHandlerBase;
import com.wire.bots.sdk.WireClient;
import com.wire.bots.sdk.models.TextMessage;
import com.wire.bots.sdk.server.model.NewBot;

public class MessageHandler extends MessageHandlerBase {
    private final BotConfig config;
    private final SessionIdentifierGenerator sesGen = new SessionIdentifierGenerator();

    MessageHandler(BotConfig config) {
        this.config = config;
    }

    @Override
    public boolean onNewBot(NewBot newBot) {
        Logger.info("New Bot: %s, Conv: %s, origin: %s", newBot.id, newBot.conversation.id, newBot.origin.name);
        return true;
    }

    @Override
    public void onNewConversation(WireClient client) {
        try {
            String botId = client.getId();
            String help = getHelp(config.getHost(), botId);
            client.sendText(help);
        } catch (Exception e) {
            Logger.error(e.getMessage());
        }
    }

    @Override
    public void onText(WireClient client, TextMessage msg) {
        try {
            if (msg.getText().equalsIgnoreCase("/help")) {
                String host = config.getHost();
                String botId = client.getId();

                String help = getHelp(host, botId);
                client.sendText(help);
            }
        } catch (Exception e) {
            Logger.error(e.getLocalizedMessage());
        }
    }

    @Override
    public void onBotRemoved(String botId) {
        Logger.info("Bot: %s got removed from the conversation :(", botId);
    }

    private String getHelp(String host, String botId) {
        return String.format("Hi, I'm JIRA-Bot. Here is how to set me up:\n\n"
                        + "1. Go to: **System / Advanced / Webhooks**\n"
                        + "2. Set **URL**: http://%s/jira/webhook/v1/%s/${project.id}",
                host,
                botId);
    }
}
