package jenkins.plugins.slack;

import hudson.ProxyConfiguration;
import jenkins.model.Jenkins;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.logging.Level;
import java.util.logging.Logger;

public class StandardSlackService implements SlackService {

    private static final Logger logger = Logger.getLogger(StandardSlackService.class.getName());

    private String host = "slack.com";
    private String teamDomain;
    private String token;
    private boolean botUser;
    private String[] roomIds;

    public StandardSlackService(String teamDomain, String token, boolean botUser, String roomId) {
        super();
        this.teamDomain = teamDomain;
        this.token = token;
        this.botUser = botUser;
        this.roomIds = roomId.split("[,; ]+");
    }

    public boolean publish(String message) {
        return publish(message, "warning");
    }

    public boolean publish(String message, String color) {
        boolean result = true;
        for (String roomId : roomIds) {
            //prepare attachments first
            JSONObject field = new JSONObject();
            field.put("short", false);
            field.put("value", message);

            JSONArray fields = new JSONArray();
            fields.put(field);

            JSONObject attachment = new JSONObject();
            attachment.put("fallback", message);
            attachment.put("color", color);
            attachment.put("fields", fields);
            JSONArray mrkdwn = new JSONArray();
            mrkdwn.put("pretext");
            mrkdwn.put("text");
            mrkdwn.put("fields");
            attachment.put("mrkdwn_in", mrkdwn);
            JSONArray attachments = new JSONArray();
            attachments.put(attachment);

            PostMethod post;
            String url;
            //prepare post methods for both requests types
            if (!botUser) {
                url = "https://" + teamDomain + "." + host + "/services/hooks/jenkins-ci?token=" + token;
                post = new PostMethod(url);
                JSONObject json = new JSONObject();

                json.put("channel", roomId);
                json.put("attachments", attachments);
                json.put("link_names", "1");

                post.addParameter("payload", json.toString());

            } else {
                url = "https://slack.com/api/chat.postMessage?token=" + token +
                        "&channel=" + roomId +
                        "&link_names=1" +
                        "&as_user=true";
                try {
                    url += "&attachments=" + URLEncoder.encode(attachments.toString(), "utf-8");
                } catch (UnsupportedEncodingException e) {
                    logger.log(Level.ALL, "Error while encoding attachments: " + e.getMessage());
                }
                post = new PostMethod(url);
            }
            logger.info("Posting: to " + roomId + " on " + teamDomain + " using " + url + ": " + message + " " + color);
            HttpClient client = getHttpClient();
            post.getParams().setContentCharset("UTF-8");

            try {
                int responseCode = client.executeMethod(post);
                String response = post.getResponseBodyAsString();
                if (responseCode != HttpStatus.SC_OK) {
                    logger.log(Level.WARNING, "Slack post may have failed. Response: " + response);
                    result = false;
                } else {
                    logger.info("Posting succeeded");
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "Error posting to Slack", e);
                result = false;
            } finally {
                post.releaseConnection();
            }
        }
        return result;
    }

    protected HttpClient getHttpClient() {
        HttpClient client = new HttpClient();
        if (Jenkins.getInstance() != null) {
            ProxyConfiguration proxy = Jenkins.getInstance().proxy;
            if (proxy != null) {
                client.getHostConfiguration().setProxy(proxy.name, proxy.port);
                String username = proxy.getUserName();
                String password = proxy.getPassword();
                // Consider it to be passed if username specified. Sufficient?
                if (username != null && !"".equals(username.trim())) {
                    logger.info("Using proxy authentication (user=" + username + ")");
                    // http://hc.apache.org/httpclient-3.x/authentication.html#Proxy_Authentication
                    // and
                    // http://svn.apache.org/viewvc/httpcomponents/oac.hc3x/trunk/src/examples/BasicAuthenticationExample.java?view=markup
                    client.getState().setProxyCredentials(AuthScope.ANY,
                            new UsernamePasswordCredentials(username, password));
                }
            }
        }
        return client;
    }

    void setHost(String host) {
        this.host = host;
    }
}
