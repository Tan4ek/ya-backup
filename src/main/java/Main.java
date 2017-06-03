import com.yandex.disk.rest.Credentials;
import com.yandex.disk.rest.ResourcesArgs;
import com.yandex.disk.rest.RestClient;
import com.yandex.disk.rest.exceptions.ServerIOException;
import com.yandex.disk.rest.exceptions.http.HttpCodeException;
import com.yandex.disk.rest.json.DiskInfo;
import com.yandex.disk.rest.json.Link;
import com.yandex.disk.rest.json.Resource;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Created by ssr on 02.06.17.
 */
public class Main {

    public static void main(String[] args) throws IOException, ServerIOException {

        Properties properties = readProperties(args);
        if (properties == null) return;

        Credentials credentials = new Credentials(properties.getProperty("user"), properties.getProperty("otoken"));

//        OkHttpClient okHttpClient = OkHttpClientFactory.makeClient()

        RestClient client = new RestClient(credentials);

        DiskInfo diskInfo = client.getDiskInfo();


        String backupFolderPath = properties.getProperty("yadisk.backup.folder");
        try {
            Resource bacupFolder = client.getResources(new ResourcesArgs.Builder().setPath(backupFolderPath).build());
//            client
        } catch (HttpCodeException e){
            if (e.getCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                //creating folder
                Link link = client.makeFolder(backupFolderPath);

            }
        }
//        bacupFolder
//        bacupFolder.
//        System.out.println(bacupFolder);

//        List<String> files = new ArrayList<>();
//        FileSystems.newFileSystem(Paths.get("/media/ssr/DATA/Photos.zip"), null)
//                .getRootDirectories().forEach(v -> {
//            try {
//                Files.walk(v, 1).forEach(f -> {
//                    if (f == null || f.getFileName() == null) {
//                        System.out.println("F is null: " + f + " ");
//                    } else {
//                        files.add(f.getFileName().toString());
//                    }
//                });
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }
//        });
//        System.out.println(files.get(0));
//        System.out.println(files.size());
//        if (true)
//            return;
//
//        ResourcesArgs fotos = new ResourcesArgs.Builder().setPath("Фотокамера").setSort(ResourcesArgs.Sort.created).build();
//        Resource resources = client.getResources(fotos);
//        System.out.println(resources);
//
//        System.out.println(resources.getResourceList().getItems().size());
//        System.out.println(diskInfo);
    }


    private static Properties readProperties(String[] args) throws IOException {
        Properties properties = new Properties();

        Path propertiesPath;
        if (args != null && args.length > 0 && args[0] != null) {
            propertiesPath = Paths.get(args[0]);
        } else {
            propertiesPath = Paths.get("properties");
        }
        if (Files.exists(propertiesPath)) {
            try (BufferedReader bufferedReader = Files.newBufferedReader(propertiesPath, StandardCharsets.UTF_8)) {
                properties.load(bufferedReader);
            }
        } else {
            System.out.println("Properties not found: " + propertiesPath);
            return null;
        }

        if (!properties.containsKey("otoken")) {
            System.out.println("otoken not defined");
            return null;
        }
        if (!properties.containsKey("user")) {
            System.out.println("user not defined");
            return null;
        }
        return properties;
    }
}
