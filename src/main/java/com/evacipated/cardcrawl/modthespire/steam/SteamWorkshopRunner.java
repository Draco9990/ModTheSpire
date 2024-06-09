package com.evacipated.cardcrawl.modthespire.steam;

import com.evacipated.cardcrawl.modthespire.ModTheSpire;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class SteamWorkshopRunner
{
    private static Process apiProcess;

    // Output to process
    private static OutputStream outputStreamWriter;
    private static PrintWriter processWriter;

    // Input from process
    private static InputStream inputStreamReader;
    private static Scanner scanner;

    private static void startAPI() throws IOException
    {
        if (apiProcess != null) {
            // API is already active
            return;
        }

        String apiPath = SteamWorkshop.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        apiPath = URLDecoder.decode(apiPath,  "utf-8");
        apiPath = new File(apiPath).getPath();

        ProcessBuilder pb = new ProcessBuilder(
            SteamSearch.findJRE(),
            "-cp", apiPath + File.pathSeparatorChar + ModTheSpire.STS_JAR,
            "com.evacipated.cardcrawl.modthespire.steam.SteamWorkshop"
        ).redirectError(ProcessBuilder.Redirect.INHERIT);

        apiProcess = pb.start();
        Runtime.getRuntime().addShutdownHook(new Thread(apiProcess::destroy));

        outputStreamWriter = apiProcess.getOutputStream();
        processWriter = new PrintWriter(outputStreamWriter, true);

        inputStreamReader = apiProcess.getInputStream();
        scanner = new Scanner(inputStreamReader);
    }

    public static List<SteamSearch.WorkshopInfo> findWorkshopInfos()
    {
        List<SteamSearch.WorkshopInfo> workshopInfos = new ArrayList<>();
        try {
            System.out.println("Searching for Workshop items...");
            startAPI();

            Gson gson = new GsonBuilder().setPrettyPrinting().create();

            processWriter.println("workshop_infos");

            String result = scanner.nextLine();
            SteamData steamData = gson.fromJson(result, SteamData.class);

            System.out.println(gson.toJson(steamData));

            ModTheSpire.LWJGL3_ENABLED = ModTheSpire.LWJGL3_ENABLED || steamData.steamDeck;
            for (SteamSearch.WorkshopInfo info : steamData.workshopInfos) {
                if (!info.hasTag("tool") && !info.hasTag("tools")) {
                    workshopInfos.add(info);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return workshopInfos;
    }

    public static void stopAPI()
    {
        if (apiProcess == null) {
            // API not active
            return;
        }

        if (apiProcess.isAlive()) {
            try {
                processWriter.println("quit");
                if (!apiProcess.waitFor(100, TimeUnit.MILLISECONDS)) {
                    apiProcess.destroy();
                }
            } catch (Exception ignored) {
                apiProcess.destroy();
            }
        }
        apiProcess = null;

        tryCloseCloseable(outputStreamWriter);
        tryCloseCloseable(processWriter);
        tryCloseCloseable(inputStreamReader);
        tryCloseCloseable(scanner);

        outputStreamWriter = null;
        processWriter = null;
        inputStreamReader = null;
        scanner = null;
    }

    private static void tryCloseCloseable(Closeable c){
        if(c != null) {
            try{
                c.close();
            }catch (Exception ignored){}
        }
    }
}
