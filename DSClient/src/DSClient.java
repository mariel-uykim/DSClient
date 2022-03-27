import java.net.*;
import java.io.*;
import java.util.*;

public class DSClient {
    private static Socket s;
    private static BufferedReader input;
    private static DataOutputStream output;
    private static String username = System.getProperty("user.name");

    //serverConn(): estalishes connection with the server
    public static boolean serverConn(String hostid, int port) {
        try {
            s = new Socket(hostid, port);
            System.out.println("Connection Success!");

            input = new BufferedReader(new InputStreamReader(s.getInputStream()));
            output = new DataOutputStream(s.getOutputStream());

            return true;
            
        }
        catch(Exception e) {
            System.out.println(e);
        }

        return false;
    }

    //initialise(): starts communication and authentication with server
    public static Boolean initialise() throws IOException{
        sendMsg("HELO");

        if(printRes(true).equals("OK")) {
            sendMsg("AUTH " + username + "\n");
        }

        if(printRes(false).equals("OK")){
            return true;
        }
        return false;
    }

    //printRes(): gets server response and prints out received message if print=true
    public static String printRes(Boolean print) throws IOException {
        String response = "";
        response = input.readLine();

        if(print) {
            System.out.println("SERVER: " + response);
        }
        return response;
    }

    //sendMsg(): sends message to server
    public static void sendMsg(String msg) throws IOException {
        output.write((msg+"\n").getBytes());
        output.flush();
    }

    //closeConn(): close connection
    public static void closeConn() {
        try {
            System.out.println("terminating");
            input.close();
            output.close();
            s.close();
        }
        catch (Exception e) {
            System.out.println(e);
        }
    }

    //getInfo(): get data from jobn response if 1 or all if 0, returns
    //number of data. If option is 1, gets servers with specific capabilities
    //if 2, gets all server
    public static int getInfo(String response, int option) throws IOException {
        if(option == 1) {
            //split response string into several parts
            String [] res = response.split(" ", 7);
            String send = "GETS Capable ";

            //combine last three digits
            for(int i = (res.length)-3; i < res.length; i++){
                send += (res[i] + " ");
            }

            sendMsg(send);
        }
        else if(option == 2) {
            sendMsg("GETS All");
        }
        String data = printRes(false);
        
        //split data if not error
        if(data.contains("DATA")){
            String [] dataRec = data.split(" ", 3);

            String nData = dataRec[1];

            if(nData == "."){
                return -1;
            }
            return Integer.parseInt(nData);
        }
        return -1;
    }

    //getServers(): stores server information 
    public static String getAllServers(int nData) throws IOException {
        //stores response on server information
        String reply = null;
        String serverInfo = "";

        for(int i = 0; i < nData; i++) {
            reply = input.readLine();

            if(i == nData-1) {
                serverInfo += (reply);
            }
            
            else {
                serverInfo += (reply + "--");
            }
        }

        sendMsg("OK");

        return serverInfo;

    }

    //getLargestServer(): Calls on to getAllServers to see all servers, 
    //and returns Largest server (last on list)
    public static ArrayList<ArrayList<String>> getLargestServer(int nData) throws IOException {
        String data = getAllServers(nData);
        int nDataFields = 9;
        int coreIndex = 4;
        int lsIdx = 0;

        String [] list = data.split("--");
        ArrayList<ArrayList<String>> largestServer = new ArrayList<ArrayList<String>>();
        String [][] servers = new String[list.length][nDataFields];

        for(int i = 0; i < list.length; i++) {
            String [] temp = list[i].split(" ");

            if(temp.length == nDataFields) {
                for(int j = 0; j < nDataFields; j++) {  
                    servers[i][j] = temp[j];
                }
            }
            
            //add to list if the same size
            if(largestServer.isEmpty() ||
               servers[i][coreIndex].equals(largestServer.get(0).get(coreIndex))) {

                largestServer.add(new ArrayList<String>());

                for(int j = 0; j < nDataFields; j++) {
                    
                    largestServer.get(lsIdx).add(servers[i][j]);

                }

                lsIdx++;
            }

            //restart list if greater
            else if(Integer.parseInt(servers[i][coreIndex]) >
                    Integer.parseInt(largestServer.get(0).get(coreIndex))) {

                lsIdx = 0;
                
                //clear current list
                largestServer.clear();
                largestServer.add(new ArrayList<String>());

                for(int j = 0; j < nDataFields; j++) {
                    
                    largestServer.get(lsIdx).add(servers[i][j]);

                }


                lsIdx++;
            }
        }
	
	System.out.println(largestServer);
        return largestServer;
    }

    //schedule(): schedules a job to DSServer. Returns false if
    //failed
    public static Boolean schedule(int jobID, String sType, int sID) throws IOException {
        sendMsg("SCHD " + jobID + " " + sType + " " + sID);
        return true;
    }

    //getJobID(): gets jobID from server response
    public static int getJobID(String response) {
        String [] data = response.split(" ");
        return Integer.parseInt(data[2]);
    }

    //DSClient(): Class instantiates server connection and communicates
    //with DS Server 
    public DSClient(String hostid, int port) {
	    
        //try to connect to server
        Boolean result = serverConn(hostid, port);

        //exit if failed to connect
        if(result == false) {

            System.out.println("Failed to conencect..");
            return;
        }

        try {

            //starts connection and authenticates with server
            if(!initialise()) {
                return;
            }

            //tells server 'redy' to start sending commands
            sendMsg("REDY");

            //stores server response in jobn variable
            String jobn = printRes(false);

            //get list of servers
            int nData = getInfo(jobn, 2);

            //quit if no servers or an error happens
            if(nData == -1) {
                sendMsg("QUIT");
            }
            
            //acknowleges server repsonse
            sendMsg("OK");

            //gets largest server and stores in array
            ArrayList<ArrayList<String>> servers = getLargestServer(nData);

            //clear input stream
            printRes(false);

            int i = 0;
            while(true) {
                if(i >= servers.size()) {
                    i = 0;
                }

                String serverType = servers.get(i).get(0);
                String serverID = servers.get(i).get(1);

                //skips current iteration when server sends JCPL data
                if(jobn.contains("JCPL")) {
                    sendMsg("REDY");
                    jobn = printRes(false);
                    if(jobn.contains("NONE")) {
                        sendMsg("QUIT");
                        break;
                    }
                    continue;
                }

                //sends a schedule to server consisting of current job id, the server type,
                //and server id
                String sched = "SCHD " + getJobID(jobn) + " " + serverType + " " + serverID;
                System.out.println("SCHED: " + sched);
                sendMsg(sched);

                i++;

                printRes(false);

                sendMsg("REDY");

                jobn = printRes(false);

                //end loop when no more jobs
                if(jobn.contains("NONE")) {
                    sendMsg("QUIT");
                    break;
                }


            }
            
        }
        catch(Exception e) {
            System.out.println(e);
        }

        //ends connection
        closeConn();
    }

    public static void main(String args[]) {
        DSClient c = new DSClient("127.0.0.1",50000);
    }
}


