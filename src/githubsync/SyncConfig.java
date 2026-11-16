package githubsync;

import arc.files.Fi;
import arc.util.serialization.Jval;

final class SyncConfig{
    String owner = "";
    String repository = "";
    String branch = "main";
    String path = "mindustry-data.zip";
    String token = "";
    String lastSha = "";

    static SyncConfig load(Fi file){
        SyncConfig config = new SyncConfig();
        if(!file.exists()) return config;
        try{
            Jval json = Jval.read(file.readString("UTF-8"));
            config.owner = json.getString("owner", "");
            config.repository = json.getString("repository", "");
            config.branch = json.getString("branch", "main");
            config.path = json.getString("path", "mindustry-data.zip");
            config.token = json.getString("token", "");
            config.lastSha = json.getString("lastSha", "");
        }catch(Exception ignored){
            // The setup dialog will let the user replace malformed configuration.
        }
        return config;
    }

    void save(Fi file){
        file.parent().mkdirs();
        file.writeString("{\n" +
            "  \"owner\": \"" + escape(owner) + "\",\n" +
            "  \"repository\": \"" + escape(repository) + "\",\n" +
            "  \"branch\": \"" + escape(branch) + "\",\n" +
            "  \"path\": \"" + escape(path) + "\",\n" +
            "  \"token\": \"" + escape(token) + "\",\n" +
            "  \"lastSha\": \"" + escape(lastSha) + "\"\n" +
            "}\n", false, "UTF-8");
    }

    boolean complete(){
        return !owner.isBlank() && !repository.isBlank() && !branch.isBlank() && !path.isBlank() && !token.isBlank();
    }

    private static String escape(String value){
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r");
    }
}
