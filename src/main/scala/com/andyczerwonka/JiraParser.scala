package com.andyczerwonka

import io.circe.{HCursor, Json}

import scala.util.Try

abstract class JiraEvent(val eventTypeLabel: String, cursor: HCursor) {
  val key = cursor.downField("issue").downField("key").as[String].getOrElse(throw new Exception("key not found"))
  val url = {
    val uriString = cursor.downField("issue").downField("self").as[String].getOrElse(throw new Exception("url not found"))
    val root = uriString.split("/").take(3).mkString("/")
    s"$root/browse/$key"
  }
  val summary = cursor
    .downField("issue")
    .downField("fields")
    .downField("summary").as[String].getOrElse(throw new Exception("title not found"))

  def description(): String
  def author(): String
  def color(): Int
}

class CommentEvent(event: String, cursor: HCursor) extends JiraEvent(event, cursor) {
  override val description = cursor
    .downField("comment")
    .downField("body").as[String].getOrElse("Missing Comment")

  override val author = cursor
    .downField("comment")
    .downField("updateAuthor")
    .downField("displayName").as[String].getOrElse("Missing Author")

  override val color = 4540783
}

class BugCreatedEvent(cursor: HCursor) extends JiraEvent("Bug Created", cursor) {
  override val description = cursor
    .downField("issue")
    .downField("fields")
    .downField("description").as[String].getOrElse("Missing Description")

  override val author = cursor
    .downField("issue")
    .downField("fields")
    .downField("creator")
    .downField("displayName").as[String].getOrElse("Missing Author")

  override val color = 16711680
}

object JiraParser {

  private def isBug(cursor: HCursor) = {
    val issyeType = cursor
      .downField("issue")
      .downField("fields")
      .downField("issuetype")
      .downField("name").as[String].getOrElse("Not a Bug")
    issyeType == "Bug"
  }

  def parse(body: Json): Try[JiraEvent] = {
    val cursor: HCursor = body.hcursor
    val event = cursor.downField("webhookEvent").as[String] map {
      case "jira:issue_created" if isBug(cursor) => new BugCreatedEvent(cursor)
      case "comment_created" => new CommentEvent("Comment Created", cursor)
      case "comment_updated" => new CommentEvent("Comment Updated", cursor)
      case et@_ => throw new Exception(s"$et is an event that we don't handle")
    }
    event.toTry
  }

}


