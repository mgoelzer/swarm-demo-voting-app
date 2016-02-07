package worker;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import java.sql.*;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;


class RedisQueue {
  public String hostname; public String ip;
  public RedisQueue(String hostname, String ip) {
    this.hostname = hostname; this.ip = ip;
  }
}

class Worker {

  // DNS lookup fmt (eg, 'redis%02d') on bounds inclusive range [min,max]
  public static List<RedisQueue> discoverRedisQueues(String fmt, int min, int max) throws Exception {
    List<RedisQueue> queues = new ArrayList<RedisQueue>();
    for (int i = min; i <= max; i++) {
      String hostname = String.format(fmt,i);
      try {
        InetAddress inetAddress = InetAddress.getByName(hostname);
	String addr = inetAddress.getHostAddress();
        queues.add(new RedisQueue(hostname,addr));
      } catch (UnknownHostException e) {
	// No such host -- ignore
      }
    }
    return queues;
  }

  public static void main(String[] args) {
    Map<String, String> env = System.getenv();
    String redisFmt = env.get("REDIS_PREFIX") + "%02d";
    int redisMax = Integer.parseInt( env.get("REDIS_MAX") );

    while (true) {
      try {
        List<RedisQueue> redisHosts = discoverRedisQueues(redisFmt,0,redisMax);

        Connection dbConn = connectToDB("pg");

        System.err.printf("Connecting to %d redis hosts:\n", redisHosts.size());
        for (int i = 0; i < redisHosts.size(); i++) {
	  RedisQueue rq = redisHosts.get(i);
	  System.err.printf("  redisHosts[%d] = '%s:%s'\n", i, rq.hostname, rq.ip);
          Jedis redis = connectToRedis(rq.ip);
          List<String> voteJSONLst;
          while ((voteJSONLst = redis.blpop(1,"votes"))!=null) {
            try {
              JSONObject voteData = new JSONObject(voteJSONLst.get(1));
              String voterID = voteData.getString("voter_id");
              String vote = voteData.getString("vote");
	      long epochMillis = voteData.getLong("ts");
              System.err.printf("    Processing vote for '%s' by '%s' from '%d':  ", vote, voterID, epochMillis);
              updateVote(dbConn, voterID, vote, epochMillis);
	    } catch (SQLException e) {
              e.printStackTrace(System.err);
            } catch (Exception e) {
              e.printStackTrace(System.err);
            }
	  }

        }
      } catch (Exception e) {
        e.printStackTrace(System.err);
      }
    }
  }

  static void updateVote(Connection dbConn, String voterID, String vote, long epochMillis) throws SQLException {
    Timestamp ts = new Timestamp(epochMillis);

    String dbgTs = (new SimpleDateFormat("yyyy-MM-dd HH:mm.ss")).format(new Timestamp(epochMillis));
    //System.err.printf("ts = %s\n",dbgTs);

    PreparedStatement insert = dbConn.prepareStatement(
      "INSERT INTO votes (id, vote, ts) VALUES (?, ?, ?)");
    insert.setString(1, voterID);
    insert.setString(2, vote);
    insert.setTimestamp(3, ts);

    try {
      insert.executeUpdate();
      System.err.printf("successful insert for '%s'\n", voterID);
    } catch (SQLException e) {
      PreparedStatement update = dbConn.prepareStatement(
        "UPDATE votes SET vote = ?, ts = ? WHERE id = ? AND ts < ?");
      update.setString(1, vote);
      update.setTimestamp(2, ts);
      update.setString(3, voterID);
      update.setTimestamp(4, ts);
      int rowsAffected = update.executeUpdate();
      System.err.printf("%d rows updated for '%s' (%s)\n", rowsAffected, voterID, dbgTs);
    }
  }

  static Jedis connectToRedis(String host) {
    Jedis conn = new Jedis(host);

    while (true) {
      try {
        conn.keys("*");
        break;
      } catch (JedisConnectionException e) {
        System.err.println("Failed to connect to redis - retrying");
        sleep(1000);
      }
    }

    return conn;
  }

  static Connection connectToDB(String host) throws SQLException {
    Connection conn = null;
    String password = "pg8675309";

    try {

      Class.forName("org.postgresql.Driver");
      String url = "jdbc:postgresql://" + host + "/postgres";

      while (conn == null) {
        try {
	  //Properties props = new Properties();
          //props.setProperty("user","postgres");
          conn = DriverManager.getConnection(url, "postgres", password);
        } catch (SQLException e) {
          System.err.println("Failed to connect to db - retrying");
          sleep(1000);
        }
      }

      PreparedStatement st = conn.prepareStatement(
        "CREATE TABLE IF NOT EXISTS votes (id VARCHAR(255) NOT NULL UNIQUE, vote VARCHAR(255) NOT NULL, ts TIMESTAMP DEFAULT NOW())");
      st.executeUpdate();

    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      System.exit(1);
    }

    return conn;
  }

  static void sleep(long duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      System.exit(1);
    }
  }
}
