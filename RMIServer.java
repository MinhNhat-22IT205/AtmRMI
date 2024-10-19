import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

interface UserService extends Remote {
    boolean register(String username, String password) throws RemoteException;
    boolean login(String username, String password) throws RemoteException;
    int getBalance(String username) throws RemoteException;
    boolean transferMoney(String fromUsername, String toUsername, int amount) throws RemoteException;
    List<String> getTransactionHistory(String username) throws RemoteException;
    boolean canBeginTransaction(String username) throws RemoteException;
    void createSession(String username) throws RemoteException;
    void deleteSession(String username)throws RemoteException;
}

class UserServiceImpl extends UnicastRemoteObject implements UserService {
    private Connection connection;
    private final Map<String, String> activeSessions = new HashMap<>();
    
    public UserServiceImpl() throws RemoteException {
        try {
            connection = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/bank", "root", "");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    
    @Override
    public boolean canBeginTransaction(String username) throws RemoteException {
    	return !activeSessions.containsKey(username);
    }
    @Override
    public void createSession(String username) throws RemoteException {
    	if(activeSessions.containsKey(username)) return;
    	activeSessions.put(username, "");
    }
    @Override
    public void deleteSession(String username)throws RemoteException{
    	if(activeSessions.containsKey(username)) {
    		activeSessions.remove(username);
    	}
    }

    @Override
    public synchronized boolean register(String username, String password) throws RemoteException {
        if (username == null || username.isEmpty()) {
            System.out.println("Tên bị trống");
            return false;
        }
        if (password == null || password.isEmpty()) {
            System.out.println("Mật khẩu trống");
            return false;
        }

        try {
            String query = "INSERT INTO user (username, pass) VALUES (?, ?)";
            PreparedStatement stmt = connection.prepareStatement(query, Statement.RETURN_GENERATED_KEYS);
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.executeUpdate();

            ResultSet generatedKeys = stmt.getGeneratedKeys();
            if (generatedKeys.next()) {
                int userId = generatedKeys.getInt(1);
                String balanceQuery = "INSERT INTO balance (user_id, money) VALUES (?, 5000000)";
                PreparedStatement balanceStmt = connection.prepareStatement(balanceQuery);
                balanceStmt.setInt(1, userId);
                balanceStmt.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean login(String username, String password) throws RemoteException {
        try {
            String query = "SELECT * FROM user WHERE username = ? AND pass = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public int getBalance(String username) throws RemoteException {
        try {
            String query = "SELECT balance.money FROM balance INNER JOIN user ON user.id = balance.user_id WHERE user.username = ?";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("money");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    @Override
    public boolean transferMoney(String fromUsername, String toUsername, int amount) throws RemoteException {
        if (fromUsername.equals(toUsername)) {
            System.out.println("Users cannot transfer money to themselves.");
            return false;
        }

        try {
            connection.setAutoCommit(false);
            String getUserBalance = "SELECT balance.money, user.id FROM balance INNER JOIN user ON user.id = balance.user_id WHERE user.username = ?";
            PreparedStatement fromStmt = connection.prepareStatement(getUserBalance);
            fromStmt.setString(1, fromUsername);
            ResultSet fromRs = fromStmt.executeQuery();

            if (!fromRs.next() || fromRs.getInt("money") < amount) {
                connection.rollback();
                return false;
            }

            int fromUserId = fromRs.getInt("id");
            int fromUserBalance = fromRs.getInt("money");

            PreparedStatement toStmt = connection.prepareStatement(getUserBalance);
            toStmt.setString(1, toUsername);
            ResultSet toRs = toStmt.executeQuery();
            if (!toRs.next()) {
                connection.rollback();
                return false;
            }

            int toUserId = toRs.getInt("id");

            String updateFromUser = "UPDATE balance SET money = ? WHERE user_id = ?";
            PreparedStatement updateFromStmt = connection.prepareStatement(updateFromUser);
            updateFromStmt.setInt(1, fromUserBalance - amount);
            updateFromStmt.setInt(2, fromUserId);
            updateFromStmt.executeUpdate();

            String updateToUser = "UPDATE balance SET money = money + ? WHERE user_id = ?";
            PreparedStatement updateToStmt = connection.prepareStatement(updateToUser);
            updateToStmt.setInt(1, amount);
            updateToStmt.setInt(2, toUserId);
            updateToStmt.executeUpdate();

            String logTransaction = "INSERT INTO transaction_history (user_transfer, user_receive, amount) VALUES (?, ?, ?)";
            PreparedStatement logStmt = connection.prepareStatement(logTransaction);
            logStmt.setInt(1, fromUserId);
            logStmt.setInt(2, toUserId);
            logStmt.setInt(3, amount);
            logStmt.executeUpdate();

            connection.commit();
            connection.setAutoCommit(true);
            return true;
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException rollbackEx) {
                rollbackEx.printStackTrace();
            }
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<String> getTransactionHistory(String username) throws RemoteException {
        List<String> history = new ArrayList<>();
        try {
            String query = "SELECT user_transfer, user_receive, amount, datetime " +
                    "FROM transaction_history INNER JOIN user u1 ON transaction_history.user_transfer = u1.id " +
                    "INNER JOIN user u2 ON transaction_history.user_receive = u2.id " +
                    "WHERE u1.username = ? OR u2.username = ? ORDER BY datetime DESC";
            PreparedStatement stmt = connection.prepareStatement(query);
            stmt.setString(1, username);
            stmt.setString(2, username);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                String transaction = rs.getString("datetime") + " - " +
                        (rs.getString("user_transfer").equals(username) ?
                                "Chuyển tiền đến " + rs.getString("user_receive") :
                                "Nhận tiền từ " + rs.getString("user_transfer")) +
                        " - Số tiền: " + rs.getInt("amount");
                history.add(transaction);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return history;
    }
}

public class RMIServer {
    public static void main(String[] args) {
        try {
            System.setProperty("java.rmi.server.hostname", "192.168.1.10");

            UserService userService = new UserServiceImpl();
            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("UserService", userService);
            System.out.println("RMI Server is running...");
            
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
