/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package myudpserver;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
/**
 *
 * @author Wilson
 */
public class App{

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws AWTException{
        //Database Connection testing
        //seed();
        // Actual Server
        new App();

    }

    private UDPServer svr;

    App() throws AWTException{

        svr = new UDPServer();
        loadBannedIPs();
        svr.run();

    }

    private void loadBannedIPs(){

        BannedIP bad_ip;

        try{

            BufferedReader fileReader = new BufferedReader(new FileReader("bannedips.con"));
            String line = fileReader.readLine();

            while(line != null){
                bad_ip = new BannedIP(line);
                svr.banned_ips.add(bad_ip);

                print("IP: ["+line+"] has been banned from file.");

                line = fileReader.readLine();
            }

            fileReader.close();
        }catch (Exception e){

            System.out.println(e);
        }

    }

    public void print(String s){
        System.out.println(s+"\n");
    }

    public static void seed(){
        Statement statement = null;
        ResultSet result = null;
        Connection DB = new ConnectDB().getConnection();

        //String query = "create table players(id SERIAL primary key, username varchar(200), password varchar(20))";
        // String query = "insert into players(username,password) values ('ken','123')";
        String query = "select * from players where username='ken'";

        try {
            statement = DB.createStatement();
            // statement.executeUpdate(query);
            result = statement.executeQuery(query);

            while(result.next()){
                System.out.println("Ken's password is "+result.getString("password"));
            }


            System.out.println("Finished Updating Table in DB!");
        } catch (Exception e) {
            System.out.println(e);
        }

    }


}


