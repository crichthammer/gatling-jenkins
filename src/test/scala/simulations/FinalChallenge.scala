package simulations

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.gatling.core.Predef._
import io.gatling.http.Predef._

import scala.concurrent.duration._
import scala.util.Random

class FinalChallenge extends Simulation {

  val httpConf = http.baseUrl("http://localhost:8080/app/")
    .header("Accept", "application/json")
    .proxy(Proxy("localhost", 8888))

  private def getProperties(propertyName: String, defaultValue: String) = {
    Option(System.getenv(propertyName))
      .orElse(Option(System.getProperty(propertyName)))
      .getOrElse(defaultValue)
  }

  val userCount: Int = getProperties("USER_COUNT", "3").toInt
  val rampDuration: Int = getProperties("RAMP_DURATION", "10").toInt
  val testDuration: Int = getProperties("TEST_DURATION", "60").toInt

  var idNumbers = (20 to 1000).iterator
  val rnd = new Random()
  val now = LocalDate.now()
  val pattern = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  def randomString(length: Int) = {
    rnd.alphanumeric.filter(_.isLetter).take(length).mkString
  }

  def getRandomDate(startDate: LocalDate, random: Random): String = {
    startDate.minusDays(random.nextInt(30)).format(pattern)
  }

  val customFeeder = Iterator.continually(Map(
    "gameId" -> idNumbers.next(),
    "name" -> ("Game-" + randomString(5)),
    "releaseDate" -> getRandomDate(now, rnd),
    "reviewScore" -> rnd.nextInt(100),
    "category" -> ("Category-" + randomString(6)),
    "rating" -> ("Rating-" + randomString(4))
  ))

  before {
    println(s"Running test with ${userCount} users")
    println(s"Ramping users over ${rampDuration} seconds")
    println(s"Total test duration: ${testDuration} seconds")
  }

  def getAllGames() = {
    exec(
      http("Get all games")
        .get("videogames")
        .check(status.is(200))
    )
  }

  def createNewGame() = {
    feed(customFeeder)
      .exec(
        http("Create new game")
          .post("videogames")
          .body(ElFileBody("bodies/NewGameTemplate.json")).asJson
          .check(status.is(200))
      )
  }

  def getNewGame() = {
    exec(
      http("Get new game")
        .get("videogames/${gameId}")
        .check(jsonPath("$.name").is("${name}"))
        .check(status.is(200))
    )
  }

  def deleteNewGame() = {
    exec(
      http("Delete new game")
        .delete("videogames/${gameId}")
        .check(status.is(200))
    )
  }

  val scn = scenario("User Journey of Final Script")
    .forever() {
      exec(getAllGames())
        .pause(2)
        .exec(createNewGame())
        .pause(2)
        .exec(getNewGame())
        .pause(2)
        .exec(deleteNewGame())
    }

  setUp(
    scn.inject(
      nothingFor(5 seconds),
      rampUsers(userCount) during(rampDuration seconds)
    )
  ).protocols(httpConf.inferHtmlResources())
    .maxDuration(testDuration seconds)

  after {
    println("Stress test completed")
  }

}
