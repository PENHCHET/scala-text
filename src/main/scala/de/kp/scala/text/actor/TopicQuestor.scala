package de.kp.scala.text.actor
/* Copyright (c) 2014 Dr. Krusche & Partner PartG
* 
* This file is part of the Scala-Text project
* (https://github.com/skrusche63/scala-text).
* 
* Scala-Text is free software: you can redistribute it and/or modify it under the
* terms of the GNU General Public License as published by the Free Software
* Foundation, either version 3 of the License, or (at your option) any later
* version.
* 
* Scala-Text is distributed in the hope that it will be useful, but WITHOUT ANY
* WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
* A PARTICULAR PURPOSE. See the GNU General Public License for more details.
* You should have received a copy of the GNU General Public License along with
* Scala-Text. 
* 
* If not, see <http://www.gnu.org/licenses/>.
*/

import akka.actor.{Actor,ActorLogging}

import de.kp.scala.text.Configuration
import de.kp.scala.text.model._

import de.kp.scala.text.redis.RedisCache
import de.kp.scala.text.sink.RedisSink

import scala.collection.mutable.ArrayBuffer

class TopicQuestor extends Actor with ActorLogging {

  implicit val ec = context.dispatcher
  val sink = new RedisSink()
  
  def receive = {

    case req:ServiceRequest => {
      
      val origin = sender    
      val uid = req.data("uid")

      req.task match {
       
        case "get:concept" => {
          /*
           * Retrieve the concepts associated with respect a certain group or cluster;
           * the requests supports paging and returns 1000 results by default
           */
          val response = if (sink.groupsExist(uid) == false) {
           failure(req, Messages.TOPICS_DO_NOT_EXIST(uid))
            
          } else {

            if (req.data.contains("group") == false) {
              failure(req,Messages.NO_GROUP_PROVIDED(uid))
              
            } else {
                
              val group = req.data("group").toInt
              if (sink.documentsExist(uid,group) == false) {
                failure(req, Messages.TOPICS_DO_NOT_EXIST(uid))                
              
              } else {
                
                val total = sink.documentsTotal(uid,group).toString
                
                if (req.data("start") == false || req.data("limit") == false) {
                  failure(req, Messages.MISSING_PARAMETERS(uid))
                  
                } else {
                
                  val start = req.data("start").toLong
                  val end = start + req.data("limit").toInt - 1
                  
                  val documents = sink.documents(uid,group,start,end)
            
                  val data = Map("uid" -> uid, "total" -> total, "concept" -> documents)
                  new ServiceResponse(req.service,req.task,data,TextStatus.SUCCESS)
                  
                }
              }
              
            } 
            
          }
           
          origin ! Serializer.serializeResponse(response)
          context.stop(self)
          
        }
        
        case "get:group" => {
          /*
           * Retrieve the text group identifiers of the clusters that
           * have been generated by the cluster analysis
           */
          val response = if (sink.groupsExist(uid) == false) {
           failure(req, Messages.TOPICS_DO_NOT_EXIST(uid))
            
          } else {
            
            val groups = sink.groups(uid)
            val total = groups.length.toString
            
            val data = Map("uid" -> uid, "total" -> total, "group" -> groups.mkString(","))
            new ServiceResponse(req.service,req.task,data,TextStatus.SUCCESS)
              
          }
           
          origin ! Serializer.serializeResponse(response)
          context.stop(self)
        
        }
        
        case _ => {
          
          val msg = Messages.TASK_IS_UNKNOWN(uid,req.task)
          
          origin ! Serializer.serializeResponse(failure(req,msg))
          context.stop(self)
           
        }
        
      }
      
    }
    
    case _ => {
      
      val origin = sender               
      val msg = Messages.REQUEST_IS_UNKNOWN()          
          
      origin ! Serializer.serializeResponse(failure(null,msg))
      context.stop(self)

    }
  
  }
  
  private def failure(req:ServiceRequest,message:String):ServiceResponse = {
    
    if (req == null) {
      val data = Map("message" -> message)
      new ServiceResponse("","",data,TextStatus.FAILURE)	
      
    } else {
      val data = Map("uid" -> req.data("uid"), "message" -> message)
      new ServiceResponse(req.service,req.task,data,TextStatus.FAILURE)	
    
    }
    
  }
}