package githubsync;

import arc.util.Http;
import arc.util.Http.HttpMethod;
import arc.util.Http.HttpStatusException;
import arc.util.serialization.Jval;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

final class GitHubClient{
    interface Success<T>{ void accept(T value); }
    interface Failure{ void accept(String message); }

    static final class RemoteFile{
        final boolean exists;
        final String sha;

        RemoteFile(boolean exists, String sha){
            this.exists = exists;
            this.sha = sha;
        }
    }

    private final SyncConfig config;

    GitHubClient(SyncConfig config){
        this.config = config;
    }

    void metadata(Success<RemoteFile> success, Failure failure){
        request(HttpMethod.GET, apiUrl() + "?ref=" + encode(config.branch), null, "application/vnd.github+json", response -> {
            Jval json = Jval.read(response);
            success.accept(new RemoteFile(true, json.getString("sha")));
        }, (status, message) -> {
            if(status == 404) success.accept(new RemoteFile(false, ""));
            else failure.accept(message);
        });
    }

    void upload(byte[] bytes, RemoteFile remote, Success<String> success, Failure failure){
        String encoded = Base64.getEncoder().encodeToString(bytes);
        String body = "{\"message\":\"Mindustry save sync\",\"content\":\"" + encoded + "\",\"branch\":\"" + json(config.branch) + "\"" +
            (remote.exists ? ",\"sha\":\"" + json(remote.sha) + "\"" : "") + "}";
        request(HttpMethod.PUT, apiUrl(), body, "application/vnd.github+json", response -> {
            Jval json = Jval.read(response);
            success.accept(json.get("content").getString("sha"));
        }, (status, message) -> failure.accept(message));
    }

    void download(Success<byte[]> success, Failure failure){
        requestBytes(apiUrl() + "?ref=" + encode(config.branch), "application/vnd.github.raw+json", success, failure);
    }

    private String apiUrl(){
        String[] segments = config.path.replace('\\', '/').split("/");
        StringBuilder path = new StringBuilder();
        for(String segment : segments){
            if(segment.isEmpty()) continue;
            if(path.length() > 0) path.append('/');
            path.append(encode(segment));
        }
        return "https://api.github.com/repos/" + encode(config.owner) + "/" + encode(config.repository) + "/contents/" + path;
    }

    private void request(HttpMethod method, String url, String body, String accept, Success<String> success, HttpFailure failure){
        Http.HttpRequest request = Http.request(method, url)
            .timeout(30000)
            .header("Accept", accept)
            .header("Authorization", "Bearer " + config.token)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Mindustry-GitHub-Save-Sync/1.0");
        if(body != null){
            request.header("Content-Type", "application/json").content(body);
        }
        request.error(error -> handleError(error, failure)).submit(response -> success.accept(response.getResultAsString()));
    }

    private void requestBytes(String url, String accept, Success<byte[]> success, Failure failure){
        Http.get(url)
            .timeout(60000)
            .header("Accept", accept)
            .header("Authorization", "Bearer " + config.token)
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", "Mindustry-GitHub-Save-Sync/1.0")
            .error(error -> handleError(error, (status, message) -> failure.accept(message)))
            .submit(response -> success.accept(response.getResult()));
    }

    private void handleError(Throwable error, HttpFailure failure){
        if(error instanceof HttpStatusException statusError){
            String details = statusError.response.getResultAsString();
            try{
                details = Jval.read(details).getString("message", details);
            }catch(Exception ignored){}
            failure.accept(statusError.status.code, "GitHub returned " + statusError.status.code + ": " + details);
        }else{
            failure.accept(-1, error.getMessage() == null ? error.toString() : error.getMessage());
        }
    }

    private static String encode(String value){
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String json(String value){
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private interface HttpFailure{ void accept(int status, String message); }
}
