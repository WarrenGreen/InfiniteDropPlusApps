package Test;

import com.green.back.DatabaseConnection;
import com.green.back.PostgresDatabaseConnection;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.Assert;

public class TestDatabaseConnection {
	DatabaseConnection databaseConnection = PostgresDatabaseConnection.getDBConnection();
	
	@Before
	public void setup() {
		databaseConnection.signUp("testUser", "poop", "tits@gmail.com");
		try {
			Thread.sleep(3000L);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	@After
	public void cleanup() {
		databaseConnection.deleteUser("testUser");
	}
	
	@Test
	public void TestsignUpFail() {
		String result = databaseConnection.signUp("testUser", "password", "ttt");
		Assert.assertEquals("Username already exists.", result);
		result = databaseConnection.signUp("ttt" , "password", "tits@gmail.com");
		Assert.assertEquals("Account with this email already exists.", result);
	}
	
	@Test
	public void TestLoginSuccess() {
		boolean loggedin = databaseConnection.login("testUser", "poop");
		Assert.assertTrue(loggedin);
	}
	
	@Test
	public void TestLoginUsernameFail() {
		boolean loggedin = databaseConnection.login("notUser", "poop");
		Assert.assertFalse(loggedin);
	}
	
	@Test
	public void TestLoginPasswordFail() {
		boolean loggedin = databaseConnection.login("testUser", "dong");
		Assert.assertFalse(loggedin);
	}

}
