package worker;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import java.sql.*;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONTokener;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.net.URL;
import java.io.InputStreamReader;
import java.io.BufferedReader;


class RedisQueue {
  public String address;
  public int port;
  public RedisQueue(String address, int port) {
    this.address = address; this.port = port;
  }
}

class Worker {
  public static List<RedisQueue> getRedisQueueIPs(String redisCatalogUrl) throws Exception {
	 List<RedisQueue> queues = new ArrayList<RedisQueue>();

	 String catalogJson = "";
	 URL url = new URL(redisCatalogUrl);
	 try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream(), "UTF-8"))) {
	   for (String line; (line = reader.readLine()) != null;) {
             catalogJson += line;
	   }
	 }
	 //System.err.printf("catalogJson:\n");
	 //System.err.printf("%s\n",catalogJson);
	 //System.err.printf("--END--\n");

	 JSONArray arr = (JSONArray) new JSONTokener(catalogJson).nextValue();
	 System.err.println("Redis containers discovered:");
	 for (int i = 0; i < arr.length(); i++) {
		JSONObject obj = (JSONObject) arr.get(i);
		String serviceName = obj.getString("ServiceName");
		String serviceAddress = obj.getString("ServiceAddress");
		int servicePort = obj.getInt("ServicePort");
		System.err.printf("  serviceName='%s', serviceAddress='%s', servicePort='%d'\n",
				serviceName, serviceAddress, servicePort);
		RedisQueue redisQueue = new RedisQueue(serviceAddress,servicePort);
		queues.add(redisQueue);
	 }

	 return queues;
  }

  public static void main(String[] args) {

    try {
      Map<String, String> env = System.getenv();
      String redisCatalogUrl = env.get("REDIS_CATALOG");
      System.err.printf("redisCatalogUrl='%s'\n",redisCatalogUrl);

      List<RedisQueue> redisHosts = getRedisQueueIPs(redisCatalogUrl);

      System.err.printf("Connecting to %d redis hosts:\n", redisHosts.size());
      Jedis[] redisArr = new Jedis[redisHosts.size()];
      for (int i = 0; i < redisHosts.size(); i++) {
	RedisQueue rq = redisHosts.get(i);
	System.err.printf("  redisHosts[%d] = '%s:%d'\n", i, rq.address, rq.port);
        redisArr[i] = connectToRedis(rq.address);
      }

      Connection dbConn = connectToDB("pg");

      System.err.println("Watching vote queue");

      while (true) {
	for (int i = 0; i < redisArr.length; i++) {
	  Jedis redis = redisArr[i];
          String voteJSON = redis.blpop(0, "votes").get(1);
          JSONObject voteData = new JSONObject(voteJSON);
          String voterID = voteData.getString("voter_id");
          String vote = voteData.getString("vote");
	  long epochMillis = voteData.getLong("ts");
          System.err.printf("Processing vote for '%s' by '%s' from '%d':  ", vote, voterID, epochMillis);
          updateVote(dbConn, voterID, vote, epochMillis);
	}
      }
    } catch (SQLException e) {
      e.printStackTrace();
      System.exit(1);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(1);
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

    System.err.println("Connected to redis");
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
