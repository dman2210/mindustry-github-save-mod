package githubsync;

import arc.Core;
import arc.files.Fi;
import arc.files.ZipFi;
import arc.struct.Seq;
import arc.util.io.Streams;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static mindustry.Vars.assetCacheDirectory;
import static mindustry.Vars.customMapDirectory;
import static mindustry.Vars.saveDirectory;
import static mindustry.Vars.schematicDirectory;
import static mindustry.Vars.tmpDirectory;

/** Mindustry-compatible game-data archives that deliberately preserve local installed mods. */
final class GameDataArchive{
    private GameDataArchive(){}

    static void exportData(Fi file) throws IOException{
        Seq<Fi> files = new Seq<>();
        files.add(Core.settings.getSettingsFile());
        files.addAll(customMapDirectory.list());
        files.addAll(saveDirectory.list());
        files.addAll(schematicDirectory.list());
        files.addAll(assetCacheDirectory.list()); // Required by some saves.
        String basePath = Core.settings.getDataDirectory().path();

        for(Fi other : files.copy()){
            Fi parent = other.parent();
            while(!files.contains(parent) && !parent.equals(Core.settings.getDataDirectory())){
                files.add(parent);
                parent = parent.parent();
            }
        }

        try(OutputStream output = file.write(false, 2048); ZipOutputStream zip = new ZipOutputStream(output)){
            for(Fi source : files){
                String path = source.path().substring(basePath.length());
                if(source.isDirectory()) path += "/";
                if(path.startsWith("/")) path = path.substring(1);
                zip.putNextEntry(new ZipEntry(path));
                if(!source.isDirectory()){
                    try(var input = source.read()){
                        Streams.copy(input, zip);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    static void importData(Fi file){
        Fi zipped = new ZipFi(file);
        Fi base = Core.settings.getDataDirectory();
        if(!zipped.child("settings.bin").exists()){
            throw new IllegalArgumentException("Not valid Mindustry game data.");
        }

        // Match Mindustry's importer, but keep locally installed mods in place. This also
        // allows importing older sync archives that included the currently-running mod ZIP.
        saveDirectory.deleteDirectory();
        assetCacheDirectory.deleteDirectory();
        tmpDirectory.deleteDirectory();

        zipped.walk(entry -> {
            String path = entry.path().replace('\\', '/');
            if(isLocalOnly(path)) return;
            entry.copyTo(base.child(path));
        });

        Core.settings.clear();
        Core.settings.load();
    }

    private static boolean isLocalOnly(String path){
        while(path.startsWith("/")) path = path.substring(1);
        return path.equals("mods") || path.startsWith("mods/") ||
            path.equals("github-save-sync") || path.startsWith("github-save-sync/");
    }
}
