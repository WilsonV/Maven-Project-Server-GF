/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package myudpserver;

import java.io.*;
import java.net.*;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.awt.*;
import java.awt.TrayIcon.MessageType;
import java.time.LocalDate;
import java.util.Base64;
// import io.github.cdimascio.dotenv.Dotenv;
// import io.github.cdimascio.dotenv.DotenvException;



/**
 *
 * @author Wilson
 */
public class UDPServer {

    private DatagramSocket socket;
    private boolean running;
    private Connection DB;

    private byte[] send_buf = new byte[256];
    final String version = "0.1.8";
    final String updateLink = "https://gamejolt.com/games/geofighter/436528";
    final String discordLink = "https://discord.gg/DKrk3fD";
    private ArrayList<Player> players = new ArrayList<Player>();
    private ArrayList<Game> games = new ArrayList<Game>();
    private ArrayList<Code> codes = new ArrayList<Code>();
    protected ArrayList<BannedIP> banned_ips = new ArrayList<BannedIP>();
    private int server_port = 42000;
    private int max_server_port = 43000;
    private int avail_port = server_port + 1;
    public boolean debugLog = true;

    // Ranks
    private Thread calculate_rank;

    // Challenge
    private Thread generate_challenge;

    public UDPServer() throws AWTException {

        try {

            DB = new ConnectDB().getConnection();
            socket = new DatagramSocket(server_port);
            print("Server Created on port " + server_port + " [version " + version + "]");
            // notification("Geo Fighter Server Started!");
            calculate_rank = new Thread(new CalculateRanks());
            calculate_rank.start();

            generate_challenge = new Thread(new Challenge());
            generate_challenge.start();

            // Dotenv dotenv = null;
            // dotenv = new Dotenv.configure().load();
            // System.out.println(String.format(
            //     "Hello World. Session is: %s.",
            //     dotenv.get("SESSION")));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() throws AWTException {
        try {
            running = true;

            while (running) {
                autoRemovePlayers();

                // cap the port
                if (avail_port >= max_server_port)
                    avail_port = server_port + 1;

                byte[] buf = new byte[2048];
                // print("waiting...");
                DatagramPacket packet = new DatagramPacket(buf, buf.length);

                socket.setSoTimeout(1000);

                try {
                    socket.receive(packet);

                    InetAddress address = packet.getAddress();
                    int port = packet.getPort();

                    log("Incoming from " + address + ":" + port);
                    // packet = new DatagramPacket(buf, buf.length, address, port);

                    String received = new String(packet.getData(), 0, buf.length).trim();

                    log(">> " + received);
                    processData(received, address, port);
                } catch (SocketTimeoutException e) {
                    // System.out.println("Stopped listening");
                }

            }

            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void processData(String msg, InetAddress address, int port) throws AWTException {

        String command = "", cmdArgs = "";
        log("processing: " + msg);

        if (msg.contains(" ")) {
            command = msg.substring(0, msg.indexOf(" "));
            log("command: " + command);
            cmdArgs = msg.substring(msg.indexOf(" ") + 1);
        }

        String value;

        // check if it is from a banned IP
        boolean banned = false;
        for (BannedIP b : banned_ips) {

            if (b.ip.equals(address)) {

                banned = true;
                log("Message From Banned IP Recieved. [" + address + "]");
                // Say nothing
                // sendMessage("banned",address,port);
                break;
            }

        }

        if (banned)
            return;
        /** NO COMMANDS BEFORE BAN CHECK */
        /** NO COMMANDS BEFORE BAN CHECK */
        /** NO COMMANDS BEFORE BAN CHECK */

        // someone is checking if the server is online
        if (command.equals("server_online")) {

            sendMessage("online", address, port);

            return;
        }

        // online player list
        if (msg.equals("onlinelist")) {

            String playerList = "";
            for (Player i : players) {

                playerList = playerList + i.name + "<!>";
            }

            if (!playerList.equals("")) {
                log("Player is :[" + playerList + "]");
                sendMessage("playerlist" + playerList, address, port);
            }
            return;
        }

        // Checking Version
        if (command.equals("vrs")) {
            value = cmdArgs;

            if (value.equals(version)) {
                sendMessage("goodvrs", address, port);
            } else {
                sendMessage("udl" + updateLink, address, port);
                sendMessage("oud" + version, address, port);
            }

            return;
        }

        // Chat Message
        if (msg.startsWith("msg")) {

            String sender_name = "";
            Player sender = null;

            // Which player was this from...
            for (Player i : players) {

                if (i.address.equals(address) && i.port == port) {
                    sender_name = i.name;
                    sender = i;
                    break;
                }
            }

            passChatToAllPlayers("msg" + sender_name + ": " + msg.substring(3), sender);
            print(sender_name + ": " + msg.substring(3));
            return;
        }

        // Login
        if (command.equals("login")) {
            String name = cmdArgs.substring(0, cmdArgs.indexOf("!"));
            String password = cmdArgs.substring(cmdArgs.indexOf("!") + 1);

            sendMessage(loginAccount(name, password), address, port);

            return;
        }

        // Get list of player's characters
        if (command.equals("characterlist")) {

            String list = "";

            list = getCharacterList(cmdArgs);

            list = list.replace("[", "").replace("]", "").trim();

            sendMessage(list, address, port);

            return;

        }

        // Get players tokens on
        if (command.equals("loadtokens")) {

            sendMessage(getCharacterTokens(cmdArgs), address, port);

            return;
        }

        // Get player quest
        if (command.equals("loadquest")) {

            sendMessage(getCharacterQuest(cmdArgs), address, port);

            return;
        }

        // Get player badges
        if (command.equals("loadbadges")) {

            sendMessage(getCharacterBadges(cmdArgs), address, port);

            return;
        }

        // Get player stats
        if (command.equals("loadstats")) {

            sendMessage(getCharacterStats(cmdArgs), address, port);

            return;
        }

        // Get RP stats
        if (command.equals("loadrp")) {

            sendMessage(getCharacterRankStats(cmdArgs), address, port);

            return;
        }

        if (command.equals("loadstorytrack")) {

            sendMessage(getCharacterStory(cmdArgs), address, port);
            return;
        }

        if (command.equals("loadequip")) {

            sendMessage(getCharacterEquip(cmdArgs), address, port);
            return;
        }

        if (command.equals("loadachievements")) {

            sendMessage(getCharacterAchievements(cmdArgs), address, port);
            return;
        }

        if (command.equals("loadlifetimes")) {

            sendMessage(getCharacterLifeTimes(cmdArgs), address, port);
            return;
        }

        if (msg.equals("loadchallenge")) {

            sendMessage(getChallenge(), address, port);
            return;
        }

        if (command.equals("loadstory")) {

            sendMessage(getStory(cmdArgs), address, port);
            return;
        }

        if (command.equals("loadinventoryhairs")) {

            sendMessage(getCharacterInventoryHairs(cmdArgs), address, port);
            return;
        }

        if (command.equals("loadinventoryfaces")) {

            sendMessage(getCharacterInventoryFaces(cmdArgs), address, port);
            return;
        }

        if (command.equals("loadinventoryshirts")) {

            sendMessage(getCharacterInventoryShirts(cmdArgs), address, port);
            return;
        }

        if (command.equals("loadinventorypants")) {

            sendMessage(getCharacterInventoryPants(cmdArgs), address, port);
            return;
        }

        if (command.equals("loadinventorysets")) {

            sendMessage(getCharacterInventorySets(cmdArgs), address, port);
            return;
        }

        if (command.equals("loadinventoryspecials")) {

            sendMessage(getCharacterInventorySpecials(cmdArgs), address, port);
            return;
        }

        if (command.equals("loadredeemedcodes")) {

            sendMessage(getCharacterRedeemedCodes(cmdArgs), address, port);
            return;
        }

        if (command.equals("loadstones")) {

            sendMessage(getCharacterStones(cmdArgs), address, port);
            return;
        }

        if (command.equals("saveachievements")) {

            sendMessage(saveCharacterAchievements(cmdArgs), address, port);
            return;
        }

        if (command.equals("savebadges")) {

            sendMessage(saveCharacterBadges(cmdArgs), address, port);
            return;
        }

        if (command.equals("savetokens")) {

            sendMessage(saveCharacterTokens(cmdArgs), address, port);
            return;
        }

        if (command.equals("savequests")) {

            sendMessage(saveCharacterQuests(cmdArgs), address, port);
            return;
        }

        if (command.equals("savelifetimes")) {

            sendMessage(saveCharacterLifeTimes(cmdArgs), address, port);
            return;
        }

        if (command.equals("saverp")) {

            sendMessage(saveCharacterRP(cmdArgs), address, port);
            return;
        }

        if (command.equals("saveequip")) {

            sendMessage(saveCharacterEquip(cmdArgs), address, port);
            return;
        }

        if (command.equals("saveinventoryhair")) {
            sendMessage(saveCharacterInventoryHair(cmdArgs), address, port);
            return;
        }

        if (command.equals("saveinventoryface")) {
            sendMessage(saveCharacterInventoryFace(cmdArgs), address, port);
            return;
        }

        if (command.equals("saveinventoryshirt")) {
            sendMessage(saveCharacterInventoryShirt(cmdArgs), address, port);
            return;
        }

        if (command.equals("saveinventorypant")) {
            sendMessage(saveCharacterInventoryPants(cmdArgs), address, port);
            return;
        }

        if (command.equals("saveinventoryset")) {
            sendMessage(saveCharacterInventorySets(cmdArgs), address, port);
            return;
        }

        if (command.equals("saveinventoryspecial")) {
            sendMessage(saveCharacterInventorySpecials(cmdArgs), address, port);
            return;
        }

        if (command.equals("saveredeemedcodes")) {
            sendMessage(saveCharacterRedeemedCodes(cmdArgs), address, port);
            return;
        }

        if (command.equals("savestats")) {
            sendMessage(saveCharacterStats(cmdArgs), address, port);
            return;
        }

        if (command.equals("savestones")) {

            sendMessage(saveCharacterStones(cmdArgs), address, port);
            return;
        }

        if (command.equals("savestorytrack")) {

            sendMessage(saveCharacterStoryTracker(cmdArgs), address, port);
            return;
        }

        // Player Logged Out
        if (msg.startsWith("plo")) {
            value = msg.substring(3);

            sendMessageFromServer("rpl" + value);
            removePlayer(value);

            print("** " + value + " has logged out **");
            return;
        }

        // Player making a new game (one was not found)
        /*
         * if(msg.substring(0,3).equals("str")){
         *
         *
         * sendMessage("prt"+avail_port,address,port);
         *
         * value = msg.substring(3);
         * Game new_game = new Game(this,value+"' Story Game",avail_port++,"Story");
         * games.add(new_game);
         * Thread new_story_game = new Thread(new_game);
         * new_story_game.start();
         *
         * //removePlayer(address,port);
         * passChatToAllPlayers("msg*"+value+"* has left to face a boss!",null);
         *
         * print("*"+msg.substring(3)+"* has left to face a boss!");
         *
         * return;
         * }
         */

        // In Game [Port]
        if (msg.startsWith("ing")) {

            value = msg.substring(3);

            for (Player i : players) {

                if (i.address.equals(address) && i.port == port) {

                    i.inGame = Integer.parseInt(value);
                    print(i.name + " is in game: " + value);
                    break;
                }
            }

            return;
        }

        // Status of the player
        if (msg.startsWith("sta")) {

            value = msg.substring(3);

            for (Player i : players) {

                if (i.address.equals(address) && i.port == port) {

                    i.status = value;
                    log(i.name + "'s status is: " + value);

                    // Check If Player Was Already in a game prior
                    if (!(i.inGame == 0) && i.status.equals("menu")) {

                        print("Sending " + i.name + " Back To Game!");
                        // return player to game [Old Host Status]
                        if (i.isHost)
                            send_buf = ("ohs" + "yes").getBytes();
                        else
                            send_buf = ("ohs" + "no").getBytes();

                        i.message(socket, send_buf);

                        send_buf = ("orl" + i.RPtoLose).getBytes();
                        i.message(socket, send_buf);

                        send_buf = ("ore" + i.RPtoEarn).getBytes();
                        i.message(socket, send_buf);

                        send_buf = ("ost" + i.stage).getBytes();
                        i.message(socket, send_buf);

                        send_buf = ("otm" + i.team).getBytes();
                        i.message(socket, send_buf);

                        send_buf = ("rtg" + i.inGame).getBytes();
                        i.message(socket, send_buf);
                    }
                    break;
                }
            }

            return;
        }

        if (msg.startsWith("hst")) {

            value = msg.substring(3);

            for (Player i : players) {

                if (i.address.equals(address) && i.port == port) {

                    if (value.equals("yes"))
                        i.isHost = true;
                    else
                        i.isHost = false;

                    log(i.name + " is host?: " + value);
                    break;
                }
            }

            return;
        }

        // rp to lose
        if (msg.startsWith("rtl")) {

            String RPtoLose = msg.substring(3);

            // Which player was this from...
            for (Player i : players) {

                if (i.address.equals(address) && i.port == port) {
                    i.RPtoLose = Integer.parseInt(RPtoLose);
                    break;
                }
            }

            return;
        }

        // rp to earn
        if (msg.startsWith("rte")) {

            String RPtoEarn = msg.substring(3);

            // Which player was this from...
            for (Player i : players) {

                if (i.address.equals(address) && i.port == port) {
                    i.RPtoEarn = Integer.parseInt(RPtoEarn);
                    break;
                }
            }

            return;
        }

        if (msg.startsWith("stg")) {

            String stageNumber = msg.substring(3);

            // Which player was this from...
            for (Player i : players) {

                if (i.address.equals(address) && i.port == port) {
                    i.stage = Integer.parseInt(stageNumber);
                    break;
                }
            }

            return;
        }

        // On team
        if (msg.startsWith("team")) {

            String onTeam = msg.substring(4);

            // Which player was this from...
            for (Player i : players) {

                if (i.address.equals(address) && i.port == port) {
                    i.team = onTeam;
                    break;
                }
            }

            return;
        }

        // Player making a new game (one was note found)
        if (msg.startsWith("pvp")) {

            sendMessage("prt" + avail_port, address, port);

            value = msg.substring(3);
            Game new_game = new Game(this, value + "' PvP Game", avail_port++, "PvP");
            games.add(new_game);
            Thread new_pvp_game = new Thread(new_game);
            new_pvp_game.start();

            // removePlayer(address,port);

            return;
        }

        // player is requesting a practice match
        if (msg.startsWith("rpm")) {

            value = msg.substring(3);
            String challenger = "";

            for (Player i : players) {
                // System.out.println("Comparing Add ["+address+"/"+i.address+"] and Port:
                // ["+port+"/"+i.port+"]");
                if (i.address.equals(address) && i.port == port) {
                    challenger = i.name;
                    break;
                }
            }

            if (!challenger.equals("")) {
                log(challenger + " issued a challenge to " + value);
                for (Player i : players) {

                    if (i.name.equals(value)) {
                        if (i.status.equals("menu")) {
                            send_buf = ("apm" + challenger).getBytes();
                            i.message(socket, send_buf);
                        } else {
                            sendMessage("xpm" + value, address, port);
                        }
                        break;
                    }
                }
            }

            return;
        }

        if (msg.startsWith("npm")) {

            value = msg.substring(3);

            String challengee = "";

            for (Player i : players) {

                if (i.address.equals(address) && i.port == port) {
                    challengee = i.name;
                    break;
                }
            }

            if (!challengee.equals("")) {
                log(challengee + " declined the challenge from " + value);
                for (Player i : players) {

                    if (i.name.equals(value)) {
                        send_buf = ("declined" + challengee).getBytes();
                        i.message(socket, send_buf);
                        break;
                    }
                }
            }

            return;
        }

        if (msg.startsWith("ypm")) {

            value = msg.substring(3);

            String challengee = "";

            for (Player i : players) {

                if (i.address.equals(address) && i.port == port) {
                    challengee = i.name;
                    break;
                }
            }

            if (!challengee.equals("")) {
                log(challengee + " accepted the challenge from " + value);

                // message the challenger
                for (Player i : players) {

                    if (i.name.equals(value)) {
                        sendMessage("g2pm" + avail_port + "A", i.address, i.port);
                        break;
                    }
                }

                // message the challengee
                sendMessage("g2pm" + avail_port + "B", address, port);

                value = msg.substring(3);
                Game new_game = new Game(this, value + "' Practice Game", avail_port++, "Practice");
                games.add(new_game);
                Thread new_practice_game = new Thread(new_game);
                new_practice_game.start();
                new_game.game_started = true;

                /*
                 * for(Player i: players){
                 *
                 * if(i.name.equals(value)){
                 * send_buf = ("accepted"+challengee).getBytes();
                 * i.message(socket, send_buf);
                 * break;
                 * }
                 * }
                 */
            }

            return;
        }

        /*
         * //Player making a new game (one was not found)
         * if(msg.substring(0,3).equals("cha")){
         *
         * sendMessage("prt"+avail_port,address,port);
         *
         * value = msg.substring(3);
         * Game new_game = new
         * Game(this,value+"' Challenge Game",avail_port++,"Challenge");
         * games.add(new_game);
         * Thread new_story_game = new Thread(new_game);
         * new_story_game.start();
         *
         * removePlayer(address,port);
         * passChatToAllPlayers("msg*"+value+"* has left to face a challenge!",null);
         * print(value+"* has left to face a challenge!");
         *
         * return;
         * }
         */

        // getting the date from the server
        if (msg.equals("date")) {

            sendMessage(LocalDate.now().toString(), address, port);

            return;
        }

        // discord link
        if (msg.startsWith("disc")) {
            sendMessage("disc" + discordLink, address, port);
            return;
        }

        // Ping
        if (msg.startsWith("ping")) {
            value = msg.substring(4);
            log(value + " has pinged the server..");
            sendMessage("pong", address, port);
            handlePlayers(value, address, port);
            return;
        }

        // Player trying to find a game,
        /*
         * if(msg.substring(0,4).equals("fstr")){
         *
         * boolean gameFound = false;
         *
         * for(Game g: games){
         * if(!g.game_started && g.playerCount < 1 && g.game_type.equals("Story")){
         * sendMessage("prt"+g.port,address,port);
         * gameFound = true;
         * //removePlayer(address,port);
         * break;
         * }
         * }
         *
         * if(!gameFound){
         * sendMessage("new",address,port);
         * }
         *
         * return;
         * }
         */

        // Player trying to find a game,
        if (msg.startsWith("fpvp")) {

            boolean gameFound = false;

            for (Game g : games) {
                if (!g.game_started && g.playerCount == 1 && g.game_type.equals("PvP")) {
                    sendMessage("prt" + g.port, address, port);
                    gameFound = true;
                    // removePlayer(address,port);
                    break;
                }
            }

            if (!gameFound) {
                sendMessage("new", address, port);
            }

            return;
        }

        // Player left to pvp
        if (msg.startsWith("f2pvp")) {

            passChatToAllPlayers("msg*" + msg.substring(5) + "* has left for some PvP!", null);
            print(msg.substring(5) + "* has left for some PvP!");
            // removePlayer(address,port);

            return;
        }

        // Player left to train
        if (msg.startsWith("ftrain")) {

            passChatToAllPlayers("msg*" + msg.substring(6) + "* has left to train!", null);
            print(msg.substring(6) + " has left to train!");
            // removePlayer(address,port);

            return;
        }

        // Player left to tutorial
        if (msg.startsWith("ftutorial")) {

            passChatToAllPlayers("msg*" + msg.substring(9) + "* has left to do the tutorial!", null);
            print(msg.substring(9) + " has left do tutorial!");
            // removePlayer(address,port);

            return;
        }

        // Player left to challenge mode
        if (msg.startsWith("fchalmode")) {

            passChatToAllPlayers("msg*" + msg.substring(9) + "* has left to face a challenge!", null);
            print(msg.substring(9) + " has left do challenge mode!");
            // removePlayer(address,port);

            return;
        }

        if (msg.startsWith("fstory")) {

            passChatToAllPlayers("msg*" + msg.substring(6) + "* has left do to story mode!", null);
            print(msg.substring(6) + " has left to do story mode.");

            return;
        }

        // New User
        if (command.equals("nuser")) {
            value = msg.substring(5);
            String name = value.substring(0, value.indexOf("!"));
            String password = value.substring(value.indexOf("!") + 1, value.indexOf("#"));
            String email = value.substring(value.indexOf("#") + 1);

            print("recieved nuser for :" + value);
            if (registerAccount(name, password, email)) {

                sendMessage("user created", address, port);
                print("** " + name + " has registered!");

            } else {

                sendMessage("failed registration", address, port);
                print("** Failed to register account for " + name + " **");
            }

            return;
        }

        // New Character
        // if (msg.startsWith("nchar")) {
        //     value = msg.substring(5);
        //     String account = value.substring(0, value.indexOf("."));
        //     String character = value.substring(value.indexOf(".") + 1);

        //     // print("New Character ["+character+"] in Account ["+account+"]");
        //     // print("Value: "+value);
        //     // print("Account: "+account);
        //     // print("Character: "+character);

        //     // Add Dir for new character
        //     new File("Users/" + account + "/Character/" + character).mkdir();

        //     try {
        //         // Write Charcter to player's list of characters
        //         FileWriter fr = new FileWriter(new File("Users/" + account + "/Character/list.con"), true);
        //         fr.write(character + "\n");
        //         fr.close();

        //         // Write character to the name taken list
        //         fr = new FileWriter(new File("Users/allcharacters.con"), true);
        //         fr.write(character + "\n");
        //         fr.close();

        //         sendMessage("char created", address, port);

        //         print("** " + account + " has registered a new character: " + character + " **");
        //     } catch (IOException e) {
        //         e.printStackTrace();
        //     }

        //     return;
        // }

        // File Transfer {Receiving a file}
        if (msg.startsWith("frecv")) {
            value = msg.substring(5);
            sendMessage("prt" + avail_port, address, port);

            log(address + " is saving a file! [" + value + "]");
            Thread frecv = new Thread(new FileReciever(avail_port++, value));
            frecv.start();

            return;
        }

        // File Transfer {Sending a file}
        if (msg.startsWith("ftran")) {
            value = msg.substring(5);

            sendMessage("prt" + avail_port, address, port);

            log(address + " requested a file! [" + value + "]");
            Thread ftran = new Thread(new FileSender(avail_port++, value));
            ftran.start();

            return;
        }

        // Check if Account Exist
        if (command.equals("accexist")) {
            sendMessage(checkIfAccountExist(cmdArgs), address, port);
            return;
        }

        // Check if Character Exist
        if (command.equals("charexist")) {
           sendMessage(checkIfCharacterExist(cmdArgs), address, port);
            return;
        }

        //Create new character
        if(command.equals("addnewcharacter")){
            sendMessage(createNewCharacter(cmdArgs), address, port);
            return;
        }

        // check a code
        if (msg.startsWith("checkcode")) {

            value = msg.substring(9);
            log("Code Recieved: " + value);
            try {

                File temp_file = new File("Codes.con");
                BufferedReader br = new BufferedReader(new FileReader(temp_file));

                try {

                    String code;
                    String item = "";
                    String itemName = "";
                    int usageLeft = 0;
                    boolean codeFound = false;

                    // load codes
                    while ((code = br.readLine()) != null) {

                        item = br.readLine();
                        itemName = br.readLine();
                        usageLeft = Integer.parseInt(br.readLine());

                        codes.add(new Code(code, item, itemName, usageLeft));
                    }

                    br.close();

                    // check if code exist

                    for (Code i : codes) {
                        log("comparing: " + i.code + "|" + value);
                        if (i.code.equals(value)) {

                            codeFound = true;

                            if (i.usageLeft > 0) {
                                sendMessage("validCode" + i.item + "!" + i.itemName, address, port);
                                i.usageLeft--;
                                log("Valid Code!");
                            } else {
                                sendMessage("codeUsed", address, port);
                                log("Code is used up!");
                            }

                            break;
                        }
                    }

                    if (codeFound == false) {
                        sendMessage("wrongCode", address, port);
                        log("Wrong Code!");
                    }

                    // save codes
                    try {
                        FileWriter f_writer = new FileWriter("Codes.con", false);

                        for (Code i : codes) {
                            log("writing code: " + i.code);
                            f_writer.write(i.code + "\n");
                            f_writer.write(i.item + "\n");
                            f_writer.write(i.itemName + "\n");
                            f_writer.write(i.usageLeft + "\n");

                        }
                        codes.clear();

                        f_writer.close();
                    } catch (IOException d) {
                        d.printStackTrace();
                    }
                } catch (IOException f) {
                    f.printStackTrace();
                }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            return;
        }

    }

    private String loginAccount(String name, String password) {

        try {

            ResultSet result = DB.createStatement()
                    .executeQuery(
                            "select id , password from accounts where username='" + name.trim().toLowerCase() + "'");

            if (result.next()) {

                if (password.equals(decode64(result.getString("password")))) {

                    // Return the ID encoded!
                    log("Logged in ID:" + result.getString("id"));
                    log("Encoded ID:" + encode64(result.getString("id")));

                    return encode64(result.getString("id"));
                } else {
                    log("Wrong password.");
                    return "";
                }

            } else {
                log("Account not found.");
                return "";
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        log("Error looking while looking for account.");
        return "";
    }

    private boolean registerAccount(String name, String password, String email) {

        log("registering on DB...");
        try {
            int result = DB.createStatement().executeUpdate("insert into accounts(username,password,email) values ('"
                    + name.trim().toLowerCase() + "','" + encode64(password) + "','" + email + "')");

            log("Create account result is:" + result);
            if (result != 0)
                return true;
            else
                return false;

        } catch (SQLException e) {

            e.printStackTrace();
        }
        return false;
    }

    private String getCharacterList(String token) {

        String finalList = "";
        try {

            ResultSet result = DB.createStatement()
                    .executeQuery("select name from characters where account='" + decode64(token) + "'");

            ArrayList<String> list = new ArrayList<String>();

            while (result.next()) {
                list.add(result.getString("name"));
            }

            finalList = list.toString();

            log("List:" + finalList);

            return finalList;

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

    }

    private String getCharacterTokens(String token) {

        String playerTokens = "";

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            ResultSet result = DB.createStatement()
                    .executeQuery("select tokens from characters where account='" + decode64(account) + "' and name='"
                            + character + "'");

            if (result.next()) {

                playerTokens = result.getString("tokens");
                log("Tokens found from server is " + playerTokens);

                return playerTokens;
            } else {

                return "0";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "0";
        }

    }

    private String getCharacterQuest(String token) {

        String playerQuest = "";

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            ResultSet result = DB.createStatement()
                    .executeQuery("select quest , questcounter from characters where account='" + decode64(account)
                            + "' and name='" + character + "'");

            if (result.next()) {

                playerQuest = result.getString("quest") + "," + result.getString("questcounter");
                log("Quest found from server is " + playerQuest);

                return playerQuest;
            } else {

                return "1,0";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "1,0";
        }

    }

    private String getCharacterBadges(String token) {

        String playerBadges = "";

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            ResultSet result = DB.createStatement()
                    .executeQuery("select badges from characters where account='" + decode64(account) + "' and name='"
                            + character + "'");

            if (result.next()) {

                playerBadges = result.getString("badges");
                log("Badges found from server is " + playerBadges);

                return playerBadges;
            } else {

                return "Newbie";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "Newbie";
        }

    }

    private String getCharacterStats(String token) {

        String playerStats = "";

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            log("getting stat for "+character);

            ResultSet result = DB.createStatement()
                    .executeQuery(
                            "select level, health, strength, defense, critical, experience, skillpoints, skillpointsused from characters where account='"+ decode64(account) + "' and name='" + character + "'");

            if (result.next()) {

                for (int i = 1; i <= 8; i++) {
                    if (i < 8)
                        playerStats += result.getString(i) + ",";
                    else
                        playerStats += result.getString(i);
                }
                log("Stats found from server is " + playerStats);

            } else {

                return "";
            }

            return playerStats;

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

    }

    private String getCharacterRankStats(String token) {

        String playerStats = "";

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            ResultSet result = DB.createStatement()
                    .executeQuery(
                            "select rankpoints, wins, losses from characters where account='"
                                    + decode64(account) + "' and name='" + character + "'");

            if (result.next()) {

                for (int i = 1; i <= 3; i++) {
                    if (i < 3)
                        playerStats += result.getString(i) + ",";
                    else
                        playerStats += result.getString(i);
                }
                log("Rank Stats found from server is " + playerStats);

            } else {

                return "";
            }

            return playerStats;

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

    }

    private String getCharacterStory(String token) {

        String playerStory = "";

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            ResultSet result = DB.createStatement()
                    .executeQuery("select story from characters where account='" + decode64(account) + "' and name='"
                            + character + "'");

            if (result.next()) {

                playerStory = result.getString("story");
                log("Story found from server is " + playerStory);

                return playerStory;
            } else {

                return "0";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "0";
        }

    }

    private String getCharacterEquip(String token) {

        String playerEquip = "";

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            ResultSet result = DB.createStatement()
                    .executeQuery(
                            "select hair, face, shirt, pant, sets.name as currentset, firstspecial.name as special1, secondspecial.name as special2, thirdspecial.name as special3, skintone, badge from characters join sets on characters.currentset = sets.id join specials firstspecial on characters.special1 = firstspecial.id join specials secondspecial on characters.special2 = secondspecial.id join specials thirdspecial on characters.special3 = thirdspecial.id where characters.account='"
                                    + decode64(account) + "' and characters.name='" + character + "'");

            if (result.next()) {

                for (int i = 1; i <= 10; i++) {
                    if (i < 10)
                        playerEquip += result.getString(i) + ",";
                    else
                        playerEquip += result.getString(i);
                }
                log("Equip found from server is " + playerEquip);

            } else {

                return "";
            }

            return playerEquip;

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

    }

    private String getCharacterAchievements(String token) {

        String playerAchievements = "";

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            ResultSet result = DB.createStatement()
                    .executeQuery(
                            "select achievements from characters where account='" + decode64(account) + "' and name='"
                                    + character + "'");

            if (result.next()) {

                playerAchievements = result.getString("achievements");
                log("Achievements found from server is " + playerAchievements);

                return playerAchievements;
            } else {

                return "";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

    }

    private String getCharacterLifeTimes(String token) {

        String playerLifeTimes = "";

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            ResultSet result = DB.createStatement()
                    .executeQuery(
                            "select lifetimetokens, lifetimecounters, lifetimedamage from characters where account='"
                                    + decode64(account) + "' and name='" + character + "'");

            if (result.next()) {

                for (int i = 1; i <= 3; i++) {
                    if (i < 3)
                        playerLifeTimes += result.getString(i) + ",";
                    else
                        playerLifeTimes += result.getString(i);
                }
                log("Life time found from server is " + playerLifeTimes);

            } else {

                return "";
            }

            return playerLifeTimes;

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

    }

    private String getCharacterInventoryHairs(String token) {

        String playerInventoryHairs = "";

        int start_location = 0;
        int end_location = -1;

        ArrayList<String> found_hair_numbers = new ArrayList<String>();

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            ResultSet result = DB.createStatement()
                    .executeQuery(
                            "select inventoryhair from characters where account='" + decode64(account) + "' and name='"
                                    + character + "'");

            if (result.next()) {

                String list = result.getString("inventoryhair");
                String found = "";

                log("List of Hairs found from server is " + list);

                if (list.length() > 0) {

                    do {

                        start_location = end_location + 1;
                        end_location = list.indexOf(",", start_location);

                        if (end_location != -1) {
                            found = list.substring(start_location, end_location);
                        } else {
                            found = list.substring(start_location);
                        }

                        log("Extracted from hair list " + found);

                        found_hair_numbers.add(found);

                    } while (end_location != -1);

                    for (int i = 0; i < found_hair_numbers.size(); i++) {

                        playerInventoryHairs += getHairDetails(found_hair_numbers.get(i));

                        // Add comma if it's not the last one
                        if (i != found_hair_numbers.size() - 1) {
                            playerInventoryHairs += ",";
                        }

                    }
                }

                log("Final Hair Inventory " + playerInventoryHairs);

                return playerInventoryHairs;
            } else {

                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String getHairDetails(String num) {
        try {
            log("Getting details for hair " + num);
            ResultSet details = DB.createStatement().executeQuery("select * from hair where id=" + num);

            if (details.next()) {

                return details.getString("name") + "!" + details.getString("id");
            } else {
                return "";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String getCharacterInventoryFaces(String token) {

        String playerInventoryFaces = "";

        int start_location = 0;
        int end_location = -1;

        ArrayList<String> found_face_numbers = new ArrayList<String>();

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            ResultSet result = DB.createStatement()
                    .executeQuery(
                            "select inventoryface from characters where account='" + decode64(account) + "' and name='"
                                    + character + "'");

            if (result.next()) {

                String list = result.getString("inventoryface");
                String found = "";

                log("List of Faces found from server is " + list);

                if (list.length() > 0) {

                    do {

                        start_location = end_location + 1;
                        end_location = list.indexOf(",", start_location);

                        log("list is " + list);
                        log("start location is " + start_location);
                        log("end location is " + end_location);

                        if (end_location != -1) {
                            found = list.substring(start_location, end_location);
                        } else {
                            found = list.substring(start_location);
                        }

                        log("Extracted from face list " + found);

                        found_face_numbers.add(found);

                    } while (end_location != -1);

                    for (int i = 0; i < found_face_numbers.size(); i++) {

                        playerInventoryFaces += getFaceDetails(found_face_numbers.get(i));

                        // Add comma if it's not the last one
                        if (i != found_face_numbers.size() - 1) {
                            playerInventoryFaces += ",";
                        }

                    }
                }

                log("Final Face Inventory " + playerInventoryFaces);

                return playerInventoryFaces;
            } else {

                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String getFaceDetails(String num) {
        try {
            log("Getting details for face " + num);
            ResultSet details = DB.createStatement().executeQuery("select * from face where id=" + num);

            if (details.next()) {

                return details.getString("name") + "!" + details.getString("id");
            } else {
                return "";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String getCharacterInventoryShirts(String token) {

        String playerInventoryShirts = "";

        int start_location = 0;
        int end_location = -1;

        ArrayList<String> found_shirt_numbers = new ArrayList<String>();

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            ResultSet result = DB.createStatement()
                    .executeQuery(
                            "select inventoryshirt from characters where account='" + decode64(account) + "' and name='"
                                    + character + "'");

            if (result.next()) {

                String list = result.getString("inventoryshirt");
                String found = "";

                log("List of Shirts found from server is " + list);

                if (list.length() > 0) {

                    do {

                        start_location = end_location + 1;
                        end_location = list.indexOf(",", start_location);

                        if (end_location != -1) {
                            found = list.substring(start_location, end_location);
                        } else {
                            found = list.substring(start_location);
                        }

                        log("list is " + list);
                        log("Extracted from shirt list " + found);
                        log("start location is " + start_location);
                        log("end location is " + end_location);

                        found_shirt_numbers.add(found);

                    } while (end_location != -1);

                    for (int i = 0; i < found_shirt_numbers.size(); i++) {

                        playerInventoryShirts += getShirtDetails(found_shirt_numbers.get(i));

                        // Add comma if it's not the last one
                        if (i != found_shirt_numbers.size() - 1) {
                            playerInventoryShirts += ",";
                        }

                    }
                }

                log("Final Shirt Inventory " + playerInventoryShirts);

                return playerInventoryShirts;
            } else {

                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String getShirtDetails(String num) {
        try {
            log("Getting details for shirt " + num);
            ResultSet details = DB.createStatement().executeQuery("select * from shirt where id=" + num);

            if (details.next()) {

                return details.getString("name") + "!" + details.getString("id");
            } else {
                return "";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String getCharacterInventoryPants(String token) {

        String playerInventoryPants = "";

        int start_location = 0;
        int end_location = -1;

        ArrayList<String> found_pants_numbers = new ArrayList<String>();

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            ResultSet result = DB.createStatement()
                    .executeQuery(
                            "select inventorypants from characters where account='" + decode64(account) + "' and name='"
                                    + character + "'");

            if (result.next()) {

                String list = result.getString("inventorypants");
                String found = "";

                log("List of Pants found from server is " + list);

                if (list.length() > 0) {

                    do {

                        start_location = end_location + 1;
                        end_location = list.indexOf(",", start_location);

                        if (end_location != -1) {
                            found = list.substring(start_location, end_location);
                        } else {
                            found = list.substring(start_location);
                        }

                        log("list is " + list);
                        log("Extracted from shirt list " + found);
                        log("start location is " + start_location);
                        log("end location is " + end_location);

                        found_pants_numbers.add(found);

                    } while (end_location != -1);

                    for (int i = 0; i < found_pants_numbers.size(); i++) {

                        playerInventoryPants += getPantsDetails(found_pants_numbers.get(i));

                        // Add comma if it's not the last one
                        if (i != found_pants_numbers.size() - 1) {
                            playerInventoryPants += ",";
                        }

                    }
                }

                log("Final Pants Inventory " + playerInventoryPants);

                return playerInventoryPants;
            } else {

                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String getPantsDetails(String num) {
        try {
            log("Getting details for pants " + num);
            ResultSet details = DB.createStatement().executeQuery("select * from pants where id=" + num);

            if (details.next()) {

                return details.getString("name") + "!" + details.getString("id");
            } else {
                return "";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String getCharacterInventorySets(String token) {

        String playerInventorySets = "";

        int start_location = 0;
        int end_location = -1;

        ArrayList<String> found_sets_numbers = new ArrayList<String>();

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            ResultSet result = DB.createStatement()
                    .executeQuery(
                            "select inventorysets from characters where account='" + decode64(account) + "' and name='"
                                    + character + "'");

            if (result.next()) {

                String list = result.getString("inventorysets");
                String found = "";

                log("List of Sets found from server is " + list);

                if (list.length() > 0) {

                    do {

                        start_location = end_location + 1;
                        end_location = list.indexOf(",", start_location);

                        if (end_location != -1) {
                            found = list.substring(start_location, end_location);
                        } else {
                            found = list.substring(start_location);
                        }

                        log("list is " + list);
                        log("Extracted from shirt list " + found);
                        log("start location is " + start_location);
                        log("end location is " + end_location);

                        found_sets_numbers.add(found);

                    } while (end_location != -1);

                    for (int i = 0; i < found_sets_numbers.size(); i++) {

                        playerInventorySets += getSetsDetails(found_sets_numbers.get(i));

                        // Add comma if it's not the last one
                        if (i != found_sets_numbers.size() - 1) {
                            playerInventorySets += ",";
                        }

                    }
                }

                log("Final Sets Inventory " + playerInventorySets);

                return playerInventorySets;
            } else {

                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String getSetsDetails(String num) {
        try {
            log("Getting details for sets " + num);
            ResultSet details = DB.createStatement().executeQuery("select name from sets where id=" + num);

            if (details.next()) {

                return details.getString("name");
            } else {
                return "";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String getCharacterInventorySpecials(String token) {

        String playerInventorySpecials = "";

        int start_location = 0;
        int end_location = -1;

        ArrayList<String> found_specials_numbers = new ArrayList<String>();

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            ResultSet result = DB.createStatement()
                    .executeQuery(
                            "select inventoryspecials from characters where account='" + decode64(account)
                                    + "' and name='"
                                    + character + "'");

            if (result.next()) {

                String list = result.getString("inventoryspecials");
                String found = "";

                log("List of Specials found from server is " + list);

                if (list.length() > 0) {

                    do {

                        start_location = end_location + 1;
                        end_location = list.indexOf(",", start_location);

                        if (end_location != -1) {
                            found = list.substring(start_location, end_location);
                        } else {
                            found = list.substring(start_location);
                        }

                        log("list is " + list);
                        log("Extracted from shirt list " + found);
                        log("start location is " + start_location);
                        log("end location is " + end_location);

                        found_specials_numbers.add(found);

                    } while (end_location != -1);

                    for (int i = 0; i < found_specials_numbers.size(); i++) {

                        playerInventorySpecials += getSpecialsDetails(found_specials_numbers.get(i));

                        // Add comma if it's not the last one
                        if (i != found_specials_numbers.size() - 1) {
                            playerInventorySpecials += ",";
                        }

                    }
                }

                log("Final Specials Inventory " + playerInventorySpecials);

                return playerInventorySpecials;
            } else {

                return "";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String getSpecialsDetails(String num) {
        try {
            log("Getting details for specials " + num);
            ResultSet details = DB.createStatement().executeQuery("select name from specials where id=" + num);

            if (details.next()) {

                return details.getString("name");
            } else {
                return "";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    private String getCharacterRedeemedCodes(String token) {

        String playerRedeemedCodes = "";

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            ResultSet result = DB.createStatement()
                    .executeQuery(
                            "select redeemedcodes from characters where account='" + decode64(account) + "' and name='"
                                    + character + "'");

            if (result.next()) {

                playerRedeemedCodes = result.getString("redeemedcodes");
                log("Redeemed Codes found from server is " + playerRedeemedCodes);

                return playerRedeemedCodes;
            } else {

                return "";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

    }

    private String getCharacterStones(String token) {

        String playerStones = "";

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            ResultSet result = DB.createStatement()
                    .executeQuery(
                            "select firestones, icestones, spiritstones, bloodstones, lightningstones, darkstones from characters where account='"
                                    + decode64(account) + "' and name='"
                                    + character + "'");

            if (result.next()) {

                playerStones = result.getString("firestones") + "," +
                        result.getString("icestones") + "," +
                        result.getString("spiritstones") + "," +
                        result.getString("bloodstones") + "," +
                        result.getString("lightningstones") + "," +
                        result.getString("darkstones");

                log("Stones found from server is " + playerStones);

                return playerStones;
            } else {

                return "";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

    }

    private String getChallenge() {

        try {

            log("Loading challenge...");
            BufferedReader f_reader = new BufferedReader(new FileReader("Challenge/1.txt"));

            String challenge = "";
            String data = f_reader.readLine();

            while (data != null) {

                log("Data:" + data);
                challenge += data + ",";

                data = f_reader.readLine();
            }

            f_reader.close();

            log("Challenge is " + challenge);

            return challenge;

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

    }

    private String getStory(String token) {

        try {

            String storyNum = token.substring(0, token.indexOf(" "));
            String part = token.substring(token.indexOf(" ") + 1);

            log("Loading story...");
            BufferedReader f_reader = new BufferedReader(new FileReader("Story/" + storyNum + "/" + part + ".txt"));

            String story = "";
            String data = f_reader.readLine();

            while (data != null) {

                log("Data:" + data);
                story += data + ",";

                data = f_reader.readLine();
            }

            f_reader.close();

            log("Story is " + story);

            return story;

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }

    }

    // SAVE CHARACTER INFOS
    private String saveCharacterAchievements(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1, token.indexOf(":"));
            String data = token.substring(token.indexOf(":") + 1);

            int result = DB.createStatement().executeUpdate("update characters set achievements='" + data
                    + "' where account='" + decode64(account) + "' and name='" + character + "'");

            log("Saved Achievements for " + character + " result is " + result);

            if (result == 1) {

                return "success";
            } else {
                return "failed";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "failed";
        }
    }

    private String saveCharacterBadges(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1, token.indexOf(":"));
            String data = token.substring(token.indexOf(":") + 1);

            int result = DB.createStatement().executeUpdate("update characters set badges='" + data
                    + "' where account='" + decode64(account) + "' and name='" + character + "'");

            log("Saved Badges for " + character + " result is " + result);

            if (result == 1) {

                return "success";
            } else {
                return "failed";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "failed";
        }
    }

    private String saveCharacterTokens(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1, token.indexOf(":"));
            String data = token.substring(token.indexOf(":") + 1);

            int result = DB.createStatement().executeUpdate("update characters set tokens='" + data
                    + "' where account='" + decode64(account) + "' and name='" + character + "'");

            log("Saved Tokens for " + character + " result is " + result);

            if (result == 1) {

                return "success";
            } else {
                return "failed";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "failed";
        }
    }

    private String saveCharacterQuests(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1, token.indexOf(":"));
            String data = token.substring(token.indexOf(":") + 1);

            String quest = data.substring(0, data.indexOf(","));
            String questCounter = data.substring(data.indexOf(',') + 1);

            int result = DB.createStatement()
                    .executeUpdate("update characters set quest='" + quest + "', questcounter='" + questCounter
                            + "' where account='" + decode64(account) + "' and name='" + character + "'");

            log("Saved Quest for " + character + " result is " + result);

            if (result == 1) {

                return "success";
            } else {
                return "failed";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "failed";
        }
    }

    private String saveCharacterLifeTimes(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1, token.indexOf(":"));
            String data = token.substring(token.indexOf(":") + 1);

            String lifetimeTokens = data.substring(0, data.indexOf(","));
            data = data.replace(lifetimeTokens + ",", "");
            String lifetimeCounters = data.substring(0, data.indexOf(","));
            data = data.replace(lifetimeCounters + ",", "");
            String lifetimeDamage = data;

            int result = DB.createStatement()
                    .executeUpdate("update characters set lifetimetokens='" + lifetimeTokens + "', lifetimecounters='"
                            + lifetimeCounters + "', lifetimedamage='" + lifetimeDamage + "' where account='"
                            + decode64(account) + "' and name='" + character + "'");

            log("Saved Lifetimes for " + character + " result is " + result);

            if (result == 1) {

                return "success";
            } else {
                return "failed";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "failed";
        }
    }

    private String saveCharacterRP(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1, token.indexOf(":"));
            String data = token.substring(token.indexOf(":") + 1);

            String wins = data.substring(0, data.indexOf(","));
            data = data.replace(wins + ",", "");
            String losses = data.substring(0, data.indexOf(","));
            data = data.replace(losses + ",", "");
            String rankpoints = data;

            int result = DB.createStatement()
                    .executeUpdate("update characters set wins='" + wins + "', losses='" + losses + "', rankpoints='"
                            + rankpoints + "' where account='" + decode64(account) + "' and name='" + character + "'");

            log("Saved RP for " + character + " result is " + result);

            if (result == 1) {

                return "success";
            } else {
                return "failed";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "failed";
        }
    }

    private String saveCharacterEquip(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1, token.indexOf(":"));
            String list = token.substring(token.indexOf(":") + 1);

            int start_location = 0;
            int end_location = -1;
            String found;

            String currentset_id, special1_id, special2_id, special3_id;

            ArrayList<String> data = new ArrayList<String>();

            do {

                start_location = end_location + 1;
                end_location = list.indexOf(",", start_location);

                if (end_location != -1) {
                    found = list.substring(start_location, end_location);
                } else {
                    found = list.substring(start_location);
                }

                log("Extracted from save equip list " + found);

                // Data is sent in a specific order
                data.add(found);

            } while (end_location != -1);

            // Get ID for set and specials
            ResultSet result_ids = DB.createStatement().executeQuery(
                    "select sets.id as currentset, firstspecial.id as special1, secondspecial.id as special2, thirdspecial.id as special3 from sets join specials firstspecial on firstspecial.name='"
                            + data.get(5) + "' join specials secondspecial on secondspecial.name='" + data.get(6)
                            + "' join specials thirdspecial on thirdspecial.name='" + data.get(7)
                            + "' where sets.name='" + data.get(4) + "'");

            if (result_ids.next()) {

                currentset_id = result_ids.getString("currentset");
                special1_id = result_ids.getString("special1");
                special2_id = result_ids.getString("special2");
                special3_id = result_ids.getString("special3");

            } else {

                return "failed";
            }

            // Save Data
            int result = DB.createStatement()
                    .executeUpdate("update characters set hair='" + data.get(0) + "', face='" + data.get(1)
                            + "', shirt='" + data.get(2) + "', pant='" + data.get(3) + "', currentset='" + currentset_id
                            + "', special1='" + special1_id + "', special2='" + special2_id + "', special3='"
                            + special3_id + "', skintone='" + data.get(8) + "', badge='" + data.get(9)
                            + "' where account='" + decode64(account) + "' and name='" + character + "'");

            log("Saved equip for character " + character + " is " + result);

            if (result == 1) {
                return "success";
            } else {
                return "failed";
            }

        } catch (Exception e) {
            e.printStackTrace();

            return "failed";
        }
    }

    private String saveCharacterInventoryHair(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1, token.indexOf(":"));
            String list = token.substring(token.indexOf(":") + 1);

            log("Saving hair list " + list);

            int result = DB.createStatement().executeUpdate("update characters set inventoryhair='" + list
                    + "' where account='" + decode64(account) + "' and name='" + character + "'");

            if (result == 1) {
                return "success";
            } else {
                return "failed";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "failed";
        }
    }

    private String saveCharacterInventoryFace(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1, token.indexOf(":"));
            String list = token.substring(token.indexOf(":") + 1);

            log("Saving face list " + list);

            int result = DB.createStatement().executeUpdate("update characters set inventoryface='" + list
                    + "' where account='" + decode64(account) + "' and name='" + character + "'");

            if (result == 1) {
                return "success";
            } else {
                return "failed";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "failed";
        }
    }

    private String saveCharacterInventoryShirt(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1, token.indexOf(":"));
            String list = token.substring(token.indexOf(":") + 1);

            int result = DB.createStatement().executeUpdate("update characters set inventoryshirt='" + list
                    + "' where account='" + decode64(account) + "' and name='" + character + "'");

            if (result == 1) {
                return "success";
            } else {
                return "failed";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "failed";
        }
    }

    private String saveCharacterInventoryPants(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1, token.indexOf(":"));
            String list = token.substring(token.indexOf(":") + 1);

            int result = DB.createStatement().executeUpdate("update characters set inventorypants='" + list
                    + "' where account='" + decode64(account) + "' and name='" + character + "'");

            if (result == 1) {
                return "success";
            } else {
                return "failed";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "failed";
        }
    }

    private String saveCharacterInventorySets(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1, token.indexOf(":"));
            String list = token.substring(token.indexOf(":") + 1);

            int start_location = 0;
            int end_location = -1;
            String found;
            String setIdList = "";

            ArrayList<String> data = new ArrayList<String>();

            do {

                start_location = end_location + 1;
                end_location = list.indexOf(",", start_location);

                if (end_location != -1) {
                    found = list.substring(start_location, end_location);
                } else {
                    found = list.substring(start_location);
                }

                log("Extracted from save set list " + found);

                data.add(found.toLowerCase());

            } while (end_location != -1);

            // If list is empty, throw an execption
            if (data.isEmpty()) {
                throw new Exception("Set list to save for " + character + " is empty!");
            }

            // Get ID for sets
            ResultSet result_ids = DB.createStatement()
                    .executeQuery("select id from sets where" + saveSetSQLFormatHelper(data));

            while (result_ids.next()) {

                if (result_ids.isLast()) {
                    setIdList += result_ids.getString("id");
                } else {
                    setIdList += result_ids.getString("id") + ",";
                }
            }

            log("List of sets to save is " + setIdList);

            int result = DB.createStatement().executeUpdate("update characters set inventorysets='" + setIdList
                    + "' where account='" + decode64(account) + "' and name='" + character + "'");

            if (result == 1) {
                return "success";
            } else {
                return "failed";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "failed";
        }
    }

    private String saveCharacterInventorySpecials(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1, token.indexOf(":"));
            String list = token.substring(token.indexOf(":") + 1);

            int start_location = 0;
            int end_location = -1;
            String found;
            String specialIdList = "";

            ArrayList<String> data = new ArrayList<String>();

            do {

                start_location = end_location + 1;
                end_location = list.indexOf(",", start_location);

                if (end_location != -1) {
                    found = list.substring(start_location, end_location);
                } else {
                    found = list.substring(start_location);
                }

                log("Extracted from save special list " + found);

                data.add(found.toLowerCase());

            } while (end_location != -1);

            // If list is empty, throw an execption
            if (data.isEmpty()) {
                throw new Exception("Special list to save for " + character + " is empty!");
            }

            // Get ID for sets
            ResultSet result_ids = DB.createStatement()
                    .executeQuery("select id from specials where" + saveSetSQLFormatHelper(data));

            while (result_ids.next()) {

                if (result_ids.isLast()) {
                    specialIdList += result_ids.getString("id");
                } else {
                    specialIdList += result_ids.getString("id") + ",";
                }
            }

            log("List of specials to save is " + specialIdList);

            int result = DB.createStatement().executeUpdate("update characters set inventoryspecials='" + specialIdList
                    + "' where account='" + decode64(account) + "' and name='" + character + "'");

            if (result == 1) {
                return "success";
            } else {
                return "failed";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "failed";
        }
    }

    private String saveSetSQLFormatHelper(ArrayList<String> data) {

        String formatList = "";

        for (int i = 0; i < data.size(); i++) {
            if (i == data.size() - 1) {
                formatList += " name='" + data.get(i) + "'";
            } else {
                formatList += " name='" + data.get(i) + "' or";
            }
        }

        log("Format of sql sets command " + formatList);
        return formatList;
    }

    private String saveCharacterRedeemedCodes(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1, token.indexOf(":"));
            String list = token.substring(token.indexOf(":") + 1);

            log("Saving redeemed codes list " + list);

            int result = DB.createStatement().executeUpdate("update characters set redeemedcodes='" + list
                    + "' where account='" + decode64(account) + "' and name='" + character + "'");

            if (result == 1) {
                return "success";
            } else {
                return "failed";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "failed";
        }
    }

    private String saveCharacterStats(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1, token.indexOf(":"));
            String list = token.substring(token.indexOf(":") + 1);

            int start_location = 0;
            int end_location = -1;
            String found;

            ArrayList<String> data = new ArrayList<String>();

            do {

                start_location = end_location + 1;
                end_location = list.indexOf(",", start_location);

                if (end_location != -1) {
                    found = list.substring(start_location, end_location);
                } else {
                    found = list.substring(start_location);
                }

                log("Extracted from save stats list " + found);

                // Data is sent in a specific order
                data.add(found);

            } while (end_location != -1);

            // Save Data
            int result = DB.createStatement()
                    .executeUpdate("update characters set level='" + data.get(0) + "', health='" + data.get(1)
                            + "', strength='" + data.get(2) + "', defense='" + data.get(3) + "', critical='"
                            + data.get(4) + "', experience='" + data.get(5) + "', skillpoints='" + data.get(6)
                            + "', skillpointsused='" + data.get(7) + "' where account='" + decode64(account)
                            + "' and name='" + character + "'");

            log("Saved stats for character " + character + " is " + result);

            if (result == 1) {
                return "success";
            } else {
                return "failed";
            }

        } catch (Exception e) {
            e.printStackTrace();

            return "failed";
        }
    }

    private String saveCharacterStones(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1, token.indexOf(":"));
            String list = token.substring(token.indexOf(":") + 1);

            int start_location = 0;
            int end_location = -1;
            String found;

            ArrayList<String> data = new ArrayList<String>();

            do {

                start_location = end_location + 1;
                end_location = list.indexOf(",", start_location);

                if (end_location != -1) {
                    found = list.substring(start_location, end_location);
                } else {
                    found = list.substring(start_location);
                }

                log("Extracted from save stones list " + found);

                // Data is sent in a specific order
                data.add(found);

            } while (end_location != -1);

            if(data.isEmpty()){
                throw new Exception("Stone list is empty");
            }

            int result = DB.createStatement().executeUpdate("update characters set firestones='"+data.get(0)+"', icestones='"+data.get(1)+"', spiritstones='"+data.get(2)+"', lightningstones='"+data.get(3)+"', bloodstones='"+data.get(4)+", darkstones='"+data.get(5)+"' where account='"+decode64(account)+"' and name='"+character+"'");

            if(result == 1){
                return "success";
            }else{
                return "failed";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "failed";
        }

    }

    private String saveCharacterStoryTracker(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1, token.indexOf(":"));
            String list = token.substring(token.indexOf(":") + 1);

            int result = DB.createStatement().executeUpdate("update characters set story='"+list+"' where account='"+decode64(account)+"' and name='"+character+"'");

            if(result == 1){
                return "success";
            }else{
                return "failed";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "failed";
        }

    }

    private String checkIfAccountExist(String account){

        try {

            ResultSet result = DB.createStatement().executeQuery("select username from accounts where username='"+account+"'");

            if(result.next()){

                if(result.getString("username").equals(account)){
                    return "true";
                }else{
                    return "false";
                }

            }else{
                return "false";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "failed";

        }
    }

    private String checkIfCharacterExist(String character){

        try {

            ResultSet result = DB.createStatement().executeQuery("select name from characters where name='"+character+"'");

            if(result.next()){

                if(result.getString("name").equals(character)){
                    return "true";
                }else{
                    return "false";
                }

            }else{
                return "false";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "failed";

        }
    }

    private String createNewCharacter(String token) {

        try {
            String account = token.substring(0, token.indexOf(" "));
            String character = token.substring(token.indexOf(" ") + 1);

            int result = DB.createStatement().executeUpdate("insert into characters(name,account) values('"+character+"','"+decode64(account)+"')");

            if(result == 1){
                return "success";
            }else{
                return "failed";
            }

        }catch(Exception e){
            e.printStackTrace();
            return "failed";
        }
    }

    private void sendMessage(String s, InetAddress address, int port) {

        try {
            send_buf = new byte[256];
            send_buf = s.getBytes();
            DatagramPacket packet = new DatagramPacket(send_buf, send_buf.length, address, port);
            socket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private void passChatToAllPlayers(String s, Player sender) {
        // print("message to send to all: " + s);
        // send_buf = new byte[256];
        send_buf = s.getBytes();

        // If it's from a player, don't message that player, otherwise send it to all
        if (sender != null) {
            for (Player i : players) {

                if (!i.equals(sender))
                    i.message(socket, send_buf);
            }
        } else {
            for (Player i : players) {
                i.message(socket, send_buf);
            }
        }
    }

    public void sendMessageFromServer(String s) {
        passChatToAllPlayers(s, null);
    }

    private void handlePlayers(String name, InetAddress addr, int port) throws AWTException {
        // print("NAME IS:" + name);
        boolean playerExist = false;
        if (name.equals(""))
            return;

        // print("getting list...");

        for (Player i : players) {
            // print("comparing "+i.name+" to "+name);
            if (i.name.equals(name)) {
                playerExist = true;

                if (i.address.equals(addr) && i.port == port) {
                    // i.address = addr;
                    // i.port = port;
                    i.CheckIn();
                } else {
                    // kick old player
                    sendMessageFromServer("log2" + i.name);

                    // register new address and port
                    i.address = addr;
                    i.port = port;
                }
                // print("PLAYER ALREADY EXIST!");
                break;
            }
        }

        // If player is new
        if (playerExist == false) {
            print(name + " (" + addr.toString() + ") has logged in!");
            log(name + " Has Logged in!");
            players.add(new Player(name, addr, port));
            sendMessageFromServer("apl" + name);

        }
    }

    private void autoRemovePlayers() {
        // System.out.println("auto removed players was called");
        for (Player i : players) {
            if (i.dueForLogOut()) {
                print(i.name + " has timed out.");
                sendMessageFromServer("rpl" + i.name);
                removePlayer(i.name);
                break;
            }
        }
    }
    /*
     * private String getPlayerName(InetAddress addr, int port){
     *
     * String name = "";
     * for (Player i : players){
     * System.out.println("Checking with "+i.name);
     * if(i.address == addr && i.port == port){
     * name = i.name;
     * break;
     * }
     * }
     * return name;
     * }
     */

    public void removePlayerFromGame(String s) {

        for (Player i : players) {

            if (i.name.equals(s)) {
                i.status = "menu";
                i.inGame = 0;
                break;
            }
        }

    }

    public void kickAllPlayers() {

        for (Player i : players) {
            log("Kicking " + i.name);
            send_buf = ("kick" + i.name).getBytes();
            i.message(socket, send_buf);
        }
        players.clear();
    }

    public void removePlayer(String s) {
        for (Player i : players) {
            if (i.name.equals(s)) {
                players.remove(i);
                break;
            }
        }
    }

    protected void removeGame(Game g) {
        games.remove(g);
    }

    public void print(String s) {
        Timestamp timestamp = new Timestamp(System.currentTimeMillis());
        System.out.println("[" + timestamp + "]:" + s + "\n");
    }

    public void log(String s) {
        if (debugLog == true) {
            Timestamp timestamp = new Timestamp(System.currentTimeMillis());
            System.out.println("[" + timestamp + "]:" + s + "\n");
        }
    }

    public void notification(String message$) throws AWTException {
        // Obtain only one instance of the SystemTray object
        SystemTray tray = SystemTray.getSystemTray();

        // If the icon is a file
        Image image = Toolkit.getDefaultToolkit().createImage("icon.png");

        TrayIcon trayIcon = new TrayIcon(image, "Tray Demo");
        // Let the system resize the image if needed
        trayIcon.setImageAutoSize(true);
        // Set tooltip text for the tray icon
        trayIcon.setToolTip("System tray icon demo");
        tray.add(trayIcon);

        trayIcon.displayMessage("Geo Fighter Server", message$, MessageType.INFO);
    }

    public String encode64(String str) {

        // log("Starting Encoding on " + str);
        for (int i = 0; i < 3; i++) {
            // log("Encoding: " + str);
            str = Base64.getEncoder().encodeToString(str.getBytes());
        }

        // log("Final encode: " + str);
        return str;
    }

    public String decode64(String str) {

        // log("Starting Decoding on " + str);
        for (int i = 0; i < 3; i++) {
            // log("Decoding: " + str);
            str = new String(Base64.getDecoder().decode(str));
        }

        // log("Final decode: " + str);
        return str;
    }
}
