package githubsync;

import arc.util.Http;
import arc.util.Http.HttpMethod;
import arc.util.Http.HttpStatusException;
import arc.util.serialization.Jval;
import arc.struct.Seq;

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

    static final class Revision{
        final String commitSha;
        final String date;
        final String message;

        Revision(String commitSha, String date, String message){
            this.commitSha = commitSha;
            this.date = date;
            this.message = message;
        }

        String shortSha(){
            return commitSha.length() > 7 ? commitSha.substring(0, 7) : commitSha;
        }
    }

    private final SyncConfig config;

    GitHubClient(SyncConfig config){
        this.config = config;
    }

    void metadata(Success<RemoteFile> success, Failure failure){
        metadata(config.branch, success, failure);
    }

    void metadata(String ref, Success<RemoteFile> success, Failure failure){
        request(HttpMethod.GET, apiUrl() + "?ref=" + encode(ref), null, "application/vnd.github+json", response -> {
            Jval json = Jval.read(response);
            success.accept(new RemoteFile(true, json.getString("sha")));
        }, (status, message) -> {
            if(status == 404) success.accept(new RemoteFile(false, ""));
            else failure.accept(message);
        });
    }

    void revisions(Success<Seq<Revision>> success, Failure failure){
        String url = repositoryApiUrl() + "/commits?path=" + encode(config.path.replace('\\', '/')) +
            "&sha=" + encode(config.branch) + "&per_page=30";
        request(HttpMethod.GET, url, null, "application/vnd.github+json", response -> {
            Seq<Revision> revisions = new Seq<>();
            for(Jval entry : Jval.read(response).asArray()){
                Jval commit = entry.get("commit");
                Jval author = commit.get("author");
                revisions.add(new Revision(
                    entry.getString("sha"),
                    author == null ? "Unknown date" : author.getString("date", "Unknown date"),
                    commit.getString("message", "Mindustry save sync")
                ));
            }
            success.accept(revisions);
        }, (status, message) -> failure.accept(message));
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
        download(config.branch, success, failure);
    }

    void download(String ref, Success<byte[]> success, Failure failure){
        requestBytes(apiUrl() + "?ref=" + encode(ref), "application/vnd.github.raw+json", success, failure);
    }

    private String apiUrl(){
        String[] segments = config.path.replace('\\', '/').split("/");
        StringBuilder path = new StringBuilder();
        for(String segment : segments){
            if(segment.isEmpty()) continue;
            if(path.length() > 0) path.append('/');
            path.append(encode(segment));
        }
        return repositoryApiUrl() + "/contents/" + path;
    }

    private String repositoryApiUrl(){
        return "https://api.github.com/repos/" + encode(config.owner) + "/" + encode(config.repository);
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
