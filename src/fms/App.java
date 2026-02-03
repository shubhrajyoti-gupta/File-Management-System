package fms;

import fms.repository.FileRepository;
import fms.service.FileService;
import fms.ui.MainMenu;
import fms.util.ConsoleUtil;
import java.io.*;


public class App {

    public static void main(String[] args) {
        String registryDir = System.getProperty("user.home")
                           + File.separator + ".fms_data";
        try {
            FileRepository repo    = new FileRepository(registryDir);
            FileService    service = new FileService(repo);
            MainMenu       menu    = new MainMenu(service);
            menu.run();
        } catch (IOException e) {
            ConsoleUtil.error("Failed to initialise the application: " + e.getMessage());
            System.exit(1);
        }
    }
}
