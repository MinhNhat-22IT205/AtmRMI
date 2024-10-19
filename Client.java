import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class Client {
    private static String loggedInUser;

    private Socket serverSocket;
    private BufferedReader in;
    private PrintWriter out;

    public static void main(String[] args) {
        new Client().startClient();
    }

    private void startClient() {
        try {
            connectToServer();
            openLoginFrame();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void connectToServer() {
        String serverHost = "localhost"; // Adjust as necessary
        int serverPort = 12345;

        try {
            serverSocket = new Socket(serverHost, serverPort);
            in = new BufferedReader(new InputStreamReader(serverSocket.getInputStream()));
            out = new PrintWriter(serverSocket.getOutputStream(), true);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(null, "Unable to connect to server.");
            System.exit(1);
        }
    }

    // Open login frame
    private void openLoginFrame() {
        JFrame loginFrame = new JFrame("Đăng nhập");
        loginFrame.setSize(400, 200);
        loginFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        loginFrame.setLayout(new GridLayout(3, 2));

        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JButton loginButton = new JButton("Đăng nhập");
        JButton registerButton = new JButton("Đăng ký");

        loginFrame.add(new JLabel("Tên người dùng:"));
        loginFrame.add(usernameField);
        loginFrame.add(new JLabel("Mật khẩu:"));
        loginFrame.add(passwordField);
        loginFrame.add(loginButton);
        loginFrame.add(registerButton);

        loginFrame.setVisible(true);

        loginButton.addActionListener(e -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            if (attemptLogin(username, password)) {
                loggedInUser = username;
                loginFrame.dispose();
                openTransferFrame();
            } else {
                JOptionPane.showMessageDialog(loginFrame, "Đăng nhập thất bại!");
            }
        });

        registerButton.addActionListener(e -> {
            loginFrame.dispose();
            openRegisterFrame();
        });
    }

    // Method to handle login attempt via socket
    private boolean attemptLogin(String username, String password) {
        out.println("LOGIN " + username + " " + password);
        try {
            String response = in.readLine();
            return "SUCCESS".equals(response);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Open registration frame
    private void openRegisterFrame() {
        JFrame registerFrame = new JFrame("Đăng ký");
        registerFrame.setSize(400, 200);
        registerFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        registerFrame.setLayout(new GridLayout(4, 2));

        JTextField usernameField = new JTextField();
        JPasswordField passwordField = new JPasswordField();
        JPasswordField confirmPasswordField = new JPasswordField();
        JButton registerButton = new JButton("Đăng ký");

        registerFrame.add(new JLabel("Tên người dùng:"));
        registerFrame.add(usernameField);
        registerFrame.add(new JLabel("Mật khẩu:"));
        registerFrame.add(passwordField);
        registerFrame.add(new JLabel("Xác nhận mật khẩu:"));
        registerFrame.add(confirmPasswordField);
        registerFrame.add(new JLabel());
        registerFrame.add(registerButton);

        registerFrame.setVisible(true);

        registerButton.addActionListener(e -> {
            String username = usernameField.getText();
            String password = new String(passwordField.getPassword());
            String confirmPassword = new String(confirmPasswordField.getPassword());

            if (username.isEmpty()) {
                JOptionPane.showMessageDialog(registerFrame, "Tên bị trống");
                return;
            }
            if (password.isEmpty()) {
                JOptionPane.showMessageDialog(registerFrame, "Mật khẩu trống");
                return;
            }
            if (!password.equals(confirmPassword)) {
                JOptionPane.showMessageDialog(registerFrame, "Mật khẩu không khớp!");
                return;
            }

            if (attemptRegistration(username, password)) {
                JOptionPane.showMessageDialog(registerFrame, "Đăng ký thành công!");
                registerFrame.dispose();
                openLoginFrame();
            } else {
                JOptionPane.showMessageDialog(registerFrame, "Đăng ký thất bại!");
            }
        });
    }

    // Method to handle registration via socket
    private boolean attemptRegistration(String username, String password) {
        out.println("REGISTER " + username + " " + password);
        try {
            String response = in.readLine();
            return "SUCCESS".equals(response);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    // Open transfer money frame
    private void openTransferFrame() {
        JFrame transferFrame = new JFrame("Chuyển tiền - Người dùng: " + loggedInUser);
        transferFrame.setSize(400, 600);
        transferFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        transferFrame.setLayout(new GridBagLayout());

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel balanceLabel = new JLabel();
        JButton transferButton = new JButton("Chuyển tiền");

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1;
        transferFrame.add(new JLabel("Số dư hiện tại:"), gbc);

        gbc.gridx = 1; gbc.weightx = 0.2;
        transferFrame.add(balanceLabel, gbc);

        gbc.gridx = 2; gbc.weightx = 0.3;
        transferFrame.add(transferButton, gbc);

        JTextArea historyArea = new JTextArea();
        JScrollPane scrollPane = new JScrollPane(historyArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        historyArea.setEditable(false);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 3; gbc.weightx = 1; gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        transferFrame.add(scrollPane, gbc);

        updateBalance(balanceLabel);
        updateTransactionHistory(historyArea);

        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updateBalance(balanceLabel);
                updateTransactionHistory(historyArea);
            }
        }, 0, 5000);

        transferButton.addActionListener(e -> {
        	out.println("CANBEGINTRANSACTION");
        	String result;
			try {
				result = in.readLine();
				System.out.println(result);
				if(result.equals("false")) {
					JOptionPane.showMessageDialog(transferFrame, "A transaction is on-going");
					return;
				}
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
            JTextField recipientField = new JTextField();
            JTextField amountField = new JTextField();
            
            Object[] fields = {
                    "Tên người dùng người nhận:", recipientField,
                    "Số tiền muốn chuyển:", amountField
            };

            int option = JOptionPane.showConfirmDialog(
                    transferFrame, fields, "Chuyển tiền", JOptionPane.OK_CANCEL_OPTION);
            
            if (option == JOptionPane.OK_OPTION) {
                String recipient = recipientField.getText();
                int amount;
                try {
                    amount = Integer.parseInt(amountField.getText());
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(transferFrame, "Số tiền không hợp lệ!");
                    out.println("DELETESESSION");
                    return;
                }
                String transferResult = transferMoney(recipient, amount);
                if (transferResult.equals("SUCCESS")) {
                    JOptionPane.showMessageDialog(transferFrame, "Chuyển tiền thành công!");
                    updateBalance(balanceLabel);
                    updateTransactionHistory(historyArea);
                } else {
                    JOptionPane.showMessageDialog(transferFrame, transferResult);
                }
            }
            out.println("DELETESESSION");
        });

        transferFrame.setVisible(true);
    }

    // Method to transfer money via socket
    private String transferMoney(String recipient, int amount) {
        out.println("TRANSFER "  + recipient + " " + amount);
        try {
            String response = in.readLine();
            return response;
        } catch (IOException e) {
            e.printStackTrace();
            return "ERROR";
        }
    }

    private void updateBalance(JLabel balanceLabel) {
        out.println("BALANCE " + loggedInUser);
        try {
            String balance = in.readLine();
            balanceLabel.setText(balance);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateTransactionHistory(JTextArea historyArea) {
        out.println("HISTORY " + loggedInUser);
        try {
            String result=in.readLine();
            historyArea.setText("");
            String[] lines=result.split(";");
            for(String line:lines) {
            	historyArea.append(line+"\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
