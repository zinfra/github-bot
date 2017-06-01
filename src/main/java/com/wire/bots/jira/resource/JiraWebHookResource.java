package com.wire.bots.jira.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wire.bots.sdk.ClientRepo;
import com.wire.bots.sdk.Logger;
import com.wire.bots.sdk.Util;
import com.wire.bots.sdk.WireClient;
import com.wire.bots.sdk.assets.Picture;
import com.wire.bots.sdk.models.AssetKey;

import javax.imageio.ImageIO;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;

@Consumes(MediaType.APPLICATION_JSON)
@Path("/jira/webhook/v1/{botId}/{projectId}")
public class JiraWebHookResource {
    private final ClientRepo repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public JiraWebHookResource(ClientRepo repo) {
        this.repo = repo;
    }

    @POST
    public Response webHook(
            @PathParam("botId") String botId,
            @PathParam("projectId") String projectId,
            String payload) throws Exception {

        WireClient client = repo.getWireClient(botId);
        if (client == null) {
            Logger.warning("Bot previously deleted. Bot: %s", botId);
            return Response.
                    status(404).
                    build();
        }

        Logger.info("BotId: %s\nProjectId: %s\nPayload: %s", botId, projectId, payload);
        
        return Response.
                ok().
                build();
    }

    private void sendLinkPreview(WireClient client, String url, String title, String imageName) throws Exception {
        Picture preview;
        if (imageName.startsWith("http")) {
            preview = new Picture(imageName);
        } else {
            try (InputStream in = JiraWebHookResource.class.getClassLoader().getResourceAsStream("images/" + imageName + ".png")) {
                preview = new Picture(Util.toByteArray(in));
            }
        }
        preview.setPublic(true);
        AssetKey assetKey = client.uploadAsset(preview);
        preview.setAssetKey(assetKey.key);
        client.sendLinkPreview(url, title, preview);
    }

    private void sendLinkPreview(WireClient client, String url, String title, String avatarUrl,
                                 String imageName) throws Exception {
        Picture preview = null;
        avatarUrl = avatarUrl + "&s=20";
        try (InputStream in = new URL(avatarUrl).openStream()) {
            BufferedImage avatarImage = ImageIO.read(in);
            try (InputStream in2 = JiraWebHookResource.class.getClassLoader().getResourceAsStream(
                    "images/" + imageName + ".png")) {
                BufferedImage background = ImageIO.read(in2);
                Graphics g = background.getGraphics();
                g.drawImage(avatarImage, 40, 40, null);
                g.setColor(Color.WHITE);
                g.drawRect(39, 39, 41, 41);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(background, "png", baos);
                baos.flush();
                byte[] imageInByte = baos.toByteArray();
                baos.close();
                preview = new Picture(imageInByte);
                preview.setPublic(true);
                AssetKey assetKey = client.uploadAsset(preview);
                preview.setAssetKey(assetKey.key);
            }
        }
        client.sendLinkPreview(url, title, preview);
    }

}
