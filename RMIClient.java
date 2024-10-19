import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.JOptionPane;

public class RMIClient {

    private static List<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();
    private static UserService userService;

    public static void main(String[] args) {
        int serverPort = 12345;

        try {
            // Initialize the RMI registry connection
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            userService = (UserService) registry.lookup("UserService");

            try (ServerSocket serverSocket = new ServerSocket(serverPort)) {
                System.out.println("MultiCast Server is running on port " + serverPort);

                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    clientHandlers.add(clientHandler);
                    clientHandler.start();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String userName;

        public ClientHandler(Socket clientSocket) {
            this.socket = clientSocket;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                String request;
                while ((request = in.readLine()) != null) {
                    if (request.startsWith("LOGIN")) {
                        handleLogin(request);
                    } else if (request.startsWith("REGISTER")) {
                        handleSignup(request);
                    } else if (request.startsWith("TRANSFER")) {
                        handleTransfer(request);
                    } else if (request.startsWith("BALANCE")) {
                        handleBalance();
                    } else if (request.startsWith("HISTORY")) {
                        handleHistory();
                    }else if (request.startsWith("CANBEGINTRANSACTION")) {
                        boolean result=canBeginTransaction(userName);
                        out.println(result);
                    }else if (request.startsWith("DELETESESSION")) {
                        deleteSession();
                    } else {
                        out.println("UnknownCommand");
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                clientHandlers.remove(this);
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private void handleLogin(String request) {
            // Example: "login username password"
            String[] tokens = request.split(" ");
            if (tokens.length == 3) {
                String username = tokens[1];
                String password = tokens[2];
                try {
                    if (userService.login(username, password)) {
                        userName = username;
                        out.println("SUCCESS");
                    } else {
                        out.println("FAILED");
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                    out.println("ERROR");
                }
            } else {
                out.println("InvalidCommand");
            }
        }

        private void handleSignup(String request) {
            // Example: "signup username password"
            String[] tokens = request.split(" ");
            if (tokens.length == 3) {
                String username = tokens[1];
                String password = tokens[2];
                try {
                    if (userService.register(username, password)) {
                        out.println("SUCCESS");
                    } else {
                        out.println("FAILED");
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                    out.println("ERROR");
                }
            } else {
                out.println("InvalidCommand");
            }
        }

        private void handleTransfer(String request) {
            // Example: "transfer recipient amount"
            String[] tokens = request.split(" ");
            if (tokens.length == 3 && userName != null) {
                String recipient = tokens[1];
                int amount;
                try {
                    amount = Integer.parseInt(tokens[2]);
                } catch (NumberFormatException e) {
                    out.println("InvalidAmount");
                    return;
                }

                try {
                    if (userService.transferMoney(userName, recipient, amount)) {
                        out.println("SUCCESS");
                    } else {
                        out.println("TransferFailed");
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                    out.println("Error");
                }
            } else {
                out.println("InvalidCommand");
            }
        }
        private boolean canBeginTransaction(String username) {
        	try {
				if (!userService.canBeginTransaction(userName)) {
				    return false;
				}
				userService.createSession(userName);
				return true;
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				return false;
			}
        }
        private void deleteSession() {
            try {
				userService.deleteSession(userName);
			} catch (RemoteException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }

        private void handleBalance() {
            if (userName != null) {
                try {
                    int balance = userService.getBalance(userName);
                    out.println(balance);
                } catch (RemoteException e) {
                    e.printStackTrace();
                    out.println("Error");
                }
            } else {
                out.println("NotLoggedIn");
            }
        }

        private void handleHistory() {
            if (userName != null) {
                try {
                    List<String> history = userService.getTransactionHistory(userName);
                    StringBuilder historyResponse = new StringBuilder();
                    for (String entry : history) {
                        historyResponse.append(entry).append(";");
                    }
                    out.println(historyResponse.toString());
                } catch (RemoteException e) {
                    e.printStackTrace();
                    out.println("Error");
                }
            } else {
                out.println("NotLoggedIn");
            }
        }

        
    }
}
