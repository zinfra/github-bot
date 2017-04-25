package com.wire.bots.github.resource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wire.bots.github.BotConfig;
import com.wire.bots.github.model.Commit;
import com.wire.bots.github.model.GitResponse;
import com.wire.bots.sdk.ClientRepo;
import com.wire.bots.sdk.Logger;
import com.wire.bots.sdk.Util;
import com.wire.bots.sdk.WireClient;
import com.wire.bots.sdk.assets.Picture;
import com.wire.bots.sdk.models.AssetKey;

import javax.imageio.ImageIO;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;

@Consumes(MediaType.APPLICATION_JSON)
@Path("/github")
public class GitHubResource {

    private final BotConfig conf;
    private final ClientRepo repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public GitHubResource(ClientRepo repo, BotConfig conf) {
        this.repo = repo;
        this.conf = conf;
    }

    @POST
    @Path("/{botId}")
    public Response webHook(
            @HeaderParam("X-GitHub-Event") String event,
            @HeaderParam("X-Hub-Signature") String signature,
            @HeaderParam("X-GitHub-Delivery") String delivery,
            @PathParam("botId") String botId,
            String payload) throws Exception {

        Logger.info("%s\tBot: %s\t%s\tDelivery: %s", event, botId, signature, delivery);

        String secret = Util.readLine(new File(String.format("%s/%s/secret", conf.getCryptoDir(), botId)));
        String challenge = "sha1=" + Util.getHmacSHA1(payload, secret);
        if (!challenge.equals(signature)) {
            Logger.warning("Invalid Signature. Bot: %s", botId);
            return Response.
                    status(403).
                    build();
        }

        WireClient client = repo.getWireClient(botId);
        if (client == null) {
            Logger.warning("Bot previously deleted. Bot: %s", botId);
            return Response.
                    status(404).
                    build();
        }

        GitResponse response = mapper.readValue(payload, GitResponse.class);
        try {
            switch (event) {
                case "pull_request": {
                    handlePullReq(event, client, response);
                    break;
                }
                case "pull_request_review_comment": {
                    handlePrReviewComment(client, response);
                    break;
                }
                case "pull_request_review": {
                    handlePrReview(client, response);
                    break;
                }
                case "push": {
                    handlePush(client, response);
                    break;
                }
                case "issues": {
                    handleIssue(event, client, response);
                    break;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return Response.
                ok().
                build();
    }

    private void handleIssue(String event, WireClient client, GitResponse response) throws Exception {
        if (response.action == null) return;

        switch (response.action) {
            case "opened":
            case "reopened": {
                String title = String.format("[%s] New Issue #%s: %s",
                        response.repository.fullName,
                        response.issue.number,
                        response.issue.title);
                sendLinkPreview(client, response.issue.url, title, event + "_" + response.action);
                break;
            }
            case "closed": {
                String title = String.format("[%s] Issue #%s closed: %s",
                        response.repository.fullName,
                        response.issue.number,
                        response.issue.title);
                sendLinkPreview(client, response.issue.url, title, event + "_" + response.action);
                break;
            }
        }
    }

    private void handlePush(WireClient client, GitResponse response) throws Exception {
        if (!response.commits.isEmpty()) {
            String title = String.format("[%s] %s pushed %d commits",
                    response.repository.fullName,
                    response.sender.login,
                    response.commits.size());
            sendLinkPreview(client, response.compare, title, response.sender.avatarUrl);
            StringBuilder builder = new StringBuilder();
            for (Commit commit : response.commits) {
                builder.append("- ");
                builder.append(commit.message);
                builder.append("\n");
            }
            client.sendText(builder.toString());
        }
    }

    private void handlePrReview(WireClient client, GitResponse response) throws Exception {
        if (response.action == null) return;

        switch (response.action) {
            case "submitted": {
                String title;
                if ((response.review.body == null || response.review.body.isEmpty()) && !response.review.state.equals("commented")) {
                    title = String.format("[%s] %s %s PR #%s",
                            response.repository.fullName,
                            response.review.user.login,
                            response.review.state,
                            response.pr.number);
                } else {
                    title = String.format("[%s] %s %s PR #%s: %s",
                            response.repository.fullName,
                            response.review.user.login,
                            response.review.state,
                            response.pr.number,
                            response.review.body);
                }
                sendLinkPreview(client, response.pr.url, title, response.sender.avatarUrl);
                break;
            }
        }
    }

    private void handlePrReviewComment(WireClient client, GitResponse response) throws Exception {
        if (response.action == null) return;

        switch (response.action) {
            case "created": {
                String title = String.format("[%s] %s added a comment to PR #%s: %s",
                        response.repository.fullName,
                        response.comment.user.login,
                        response.pr.number,
                        response.comment.body);
                sendLinkPreview(client, response.comment.url, title, response.sender.avatarUrl);
                break;
            }
        }
    }

    private void handlePullReq(String event, WireClient client, GitResponse response) throws Exception {
        if (response.action == null) return;

        switch (response.action) {
            case "opened": {
                String title = String.format("[%s] New PR #%s: %s",
                        response.repository.fullName,
                        response.pr.number,
                        response.pr.title);
                sendLinkPreview(client, response.pr.url, title, response.sender.avatarUrl, event + "_" + response.action);
                break;
            }
            case "closed": {
                String mergedOrClosed = response.pr.merged ? "merged" : "closed";
                String title = String.format("[%s] PR #%s %s: %s",
                        response.repository.fullName,
                        response.pr.number,
                        mergedOrClosed,
                        response.pr.title);
                sendLinkPreview(client, response.pr.url, title, response.sender.avatarUrl, event + "_" + mergedOrClosed);
                break;
            }
        }
    }

    private void sendLinkPreview(WireClient client, String url, String title, String imageName) throws Exception {
        Picture preview;
        if (imageName.startsWith("http")) {
            preview = new Picture(imageName);
        } else {
            try (InputStream in = GitHubResource.class.getClassLoader().getResourceAsStream("images/" + imageName + ".png")) {
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
            try (InputStream in2 = GitHubResource.class.getClassLoader().getResourceAsStream(
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
