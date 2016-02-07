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


class RedisQueue {
  public String hostname;
  public String ip;
  public RedisQueue(String hostname, String ip) {
    this.hostname = hostname; this.ip = ip;
  }
  @Override
  public boolean equals(Object other) {
    if ((other == null) || (!(other instanceof RedisQueue))) return false;
    RedisQueue otherRedisQueue = (RedisQueue)other;
    return ((otherRedisQueue.hostname==this.hostname) && (otherRedisQueue.ip==this.ip));
  }
}

class Worker {
  // SO #16207718
  private static boolean cmp( List<?> l1, List<?> l2 ) {
    ArrayList<?> cp = new ArrayList<>( l1 );
    for ( Object o : l2 ) {
        if ( !cp.remove( o ) ) {
            return false;
        }
    }
    return cp.isEmpty();
  }

  // DNS lookup fmt (eg, 'redis%02d') on bounds inclusive range [min,max]
  public static List<RedisQueue> discoverRedisQueues(String fmt, int min, int max) throws Exception {
    List<RedisQueue> queues = new ArrayList<RedisQueue>();
    for (int i = min; i <= max; i++) {
      String hostname = String.format(fmt,i);
      try {
        InetAddress inetAddress = InetAddress.getByName(hostname);
	String addr = inetAddress.getHostAddress();
        //System.err.printf("'%s' registered at %s\n", hostname,addr);
        RedisQueue redisQueue = new RedisQueue(hostname,addr);
        queues.add(redisQueue);
      } catch (UnknownHostException e) {
	// ignore
      }
    }
    return queues;
  }

  public static void main(String[] args) {
    Map<String, String> env = System.getenv();
    String redisFmt = env.get("REDIS_PREFIX") + "%02d";

    while (true) {
      try {
        List<RedisQueue> redisHosts = discoverRedisQueues(redisFmt,0,99);

        System.err.printf("Connecting to %d redis hosts:\n", redisHosts.size());
        Jedis[] redisArr = new Jedis[redisHosts.size()];
        for (int i = 0; i < redisHosts.size(); i++) {
	  RedisQueue rq = redisHosts.get(i);
	  System.err.printf("  redisHosts[%d] = '%s:%s'\n", i, rq.hostname, rq.ip);
          redisArr[i] = connectToRedis(rq.ip);
        }

        Connection dbConn = connectToDB("pg");

        for (int i = 0; i < redisArr.length; i++) {
          while (true) {
	    Jedis redis = redisArr[i];
	    List<String> voteJSONLst = redis.blpop(10,"votes");
	    if (voteJSONLst == null) break;
            try {
              JSONObject voteData = new JSONObject(voteJSONLst.get(1));
              String voterID = voteData.getString("voter_id");
              String vote = voteData.getString("vote");
	      long epochMillis = voteData.getLong("ts");
              System.err.printf("Processing vote for '%s' by '%s' from '%d':  ", vote, voterID, epochMillis);
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
        "UPDATE votes SET vote = ? WHERE id = ? AND ts < ?");
      update.setString(1, vote);
      update.setString(2, voterID);
      update.setTimestamp(3, ts);
      int rowsAffected = update.executeUpdate();
      System.err.printf("%d rows updated for '%s'\n", rowsAffected, voterID);
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

    //System.err.println("Connected to redis");
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
