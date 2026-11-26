import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Tiny test helper: age a lot or print its row from the SQLite database. */
public final class DbTool {

    public static void main(String[] args) throws Exception {
        Class.forName("org.sqlite.JDBC");
        String db = args[0];
        String action = args[1];
        long id = Long.parseLong(args[2]);
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + db)) {
            try (java.sql.Statement pragma = connection.createStatement()) {
                pragma.execute("PRAGMA busy_timeout = 5000");
            }
            System.out.println("action=" + action + " id=" + id);
            if ("age".equals(action)) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE ah_lots SET created_at = created_at - ? WHERE id = ?")) {
                    statement.setLong(1, 691_200_000L);
                    statement.setLong(2, id);
                    System.out.println("aged=" + statement.executeUpdate());
                }
            } else {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT id, status, price, created_at FROM ah_lots WHERE id = ?")) {
                    statement.setLong(1, id);
                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            System.out.println("id=" + result.getLong("id")
                                    + " status=" + result.getString("status")
                                    + " price=" + result.getDouble("price")
                                    + " created=" + result.getLong("created_at"));
                        } else {
                            System.out.println("missing");
                        }
                    }
                }
            }
        }
    }
}
