import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.sql.*;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

public class RMIClient {

    private JFrame frame;
    private JButton startButton;
    private JButton stopButton;
    private JTable userTable;
    private DefaultTableModel tableModel;
    private Timer timer;
    private ServerSocket serverSocket;
    private boolean serverRunning = false;

    // Socket Server Components
    private static List<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();
    private static UserService userService;
    private static int serverPort = 12345;

    public RMIClient() {
        // Create the main frame
        frame = new JFrame("Socket Server Control");
        frame.setSize(600, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Create buttons
        startButton = new JButton("Start Server");
        stopButton = new JButton("Stop Server");
        stopButton.setEnabled(false);

        // Set button panel
        JPanel buttonPanel = new JPanel();
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);

        // Create table for user information
        String[] columnNames = {"ID", "Username", "Balance"};
        tableModel = new DefaultTableModel(columnNames, 0);
        userTable = new JTable(tableModel);
        JScrollPane tableScrollPane = new JScrollPane(userTable);

        // Add components to the frame
        frame.add(buttonPanel, BorderLayout.NORTH);
        frame.add(tableScrollPane, BorderLayout.CENTER);

        // Set button actions
        startButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                startServer();
            }
        });

        stopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                stopServer();
            }
        });

        // Display the frame
        frame.setVisible(true);
    }

    private void startServer() {
        try {
            // Initialize the RMI registry connection
            Registry registry = LocateRegistry.getRegistry("localhost", 1099);
            userService = (UserService) registry.lookup("UserService");

            serverSocket = new ServerSocket(serverPort);
            serverRunning = true;
            startButton.setEnabled(false);
            stopButton.setEnabled(true);
            JOptionPane.showMessageDialog(frame, "Server started successfully on port " + serverPort);

            // Start accepting client connections in a new thread
            new Thread(() -> {
                while (serverRunning) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        ClientHandler clientHandler = new ClientHandler(clientSocket);
                        clientHandlers.add(clientHandler);
                        clientHandler.start();
                    } catch (IOException e) {
                        if (serverRunning) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();

            // Start the background task to update user info from JDBC
            startUpdatingUserTable();

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Failed to start server: " + e.getMessage());
        }
    }

    private void stopServer() {
        try {
            serverRunning = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            for (ClientHandler handler : clientHandlers) {
                handler.interrupt();
            }
            clientHandlers.clear();
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            JOptionPane.showMessageDialog(frame, "Server stopped.");
            
            // Stop updating user table
            if (timer != null) {
                timer.cancel();
            }
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(frame, "Failed to stop server: " + e.getMessage());
        }
    }

    private void startUpdatingUserTable() {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                if (serverRunning) {
                    updateUserTable();
                }
            }
        }, 0, 1000); // Update every 1 second
    }

    private void updateUserTable() {
        // Fetch user data from the database and update the table
        try (Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/bank", "root", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT u.id, u.username, b.money FROM user u " +
                 "LEFT JOIN balance b ON u.id = b.user_id")) {

            // Clear existing data
            tableModel.setRowCount(0);

            // Populate table with new data
            while (rs.next()) {
                int id = rs.getInt("id");
                String username = rs.getString("username");
                int balance = rs.getInt("money");
                tableModel.addRow(new Object[]{id, username, balance});
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    // Socket Server ClientHandler class
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
                    } else if (request.startsWith("CANBEGINTRANSACTION")) {
                        boolean result = canBeginTransaction(userName);
                        out.println(result);
                    } else if (request.startsWith("DELETESESSION")) {
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new RMIClient();
            }
        });
    }
}
