/*
 * ONNXFreestyleProgramGenerator
 * Copyright (c) 2018 Alexander Merritt
 * All rights reserved. 
 * This program is free software: you can redistribute it and/or modify
 *
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
*/
 
package org.emergentorder.onnx

import java.nio.file._
import java.nio.ByteBuffer
import scala.meta._
import onnx.onnx.TensorProto
import collection.JavaConverters._
import spire.math.Number

import scala.reflect.ClassTag

object ONNXProgramGenerator extends App {

  val path = Paths.get("src/main/scala/ONNXProgram.scala");

  val params = ParamsMap.params
  val nodes = ParamsMap.nodes
  val nodeInputs = ParamsMap.nodeInputs
  val nodeOutputs = ParamsMap.nodeOutputs
  val outputs = ParamsMap.outputs
  val attributes = ParamsMap.attributes
//val sortedParamNames = params.keys.toSeq.sorted.map(x => "param_" + x)
  val ops = ParamsMap.ops
  val distinctOps = ops.distinct

  val nodesInputsOpsAndOutputs = (nodeInputs zip ops) zip nodeOutputs

  def fullSource[VV:spire.math.Numeric: ClassTag] = {
    "package org.emergentorder.onnx\n\n" +
    "import freestyle.free._\n" +
    "import cats.free.{ Free, FreeApplicative } \n" +
//               "import example.Float16\n"
    "import freestyle.free.implicits._\n" +
    "import scala.reflect.ClassTag\n" +
    "import scala.language.higherKinds\n\n" +
    "@module trait Application {\n" +
    distinctOps
      .map { x =>
        "  val " + x + ": " + x.capitalize + "\n"
      }
      .mkString("") +
    "  val dataSource: DataSource\n" +
    "  import cats.implicits._\n" +
    //Omit return type here for now
    "  def program[VV:spire.math.Numeric:ClassTag] = \n" +
    //Body of program generated here
    "    for {\n" +
    //Assume one output for now
    "      node" +
    nodeInputs(0)(0) +
    " <- dataSource.inputData[VV]\n" +
    params
      .map(x =>
        "      node" + x._1 + " <- "
          + "dataSource.getParams[VV](\"" + x._1 + "\")\n")
      .mkString("") +
    (nodesInputsOpsAndOutputs zip attributes)
      .map { x =>
        val nodesOrParams = x._1._1._1.map(y => "node" + y + """, """" + y + """"""")

//        x._2.map(y => y.getAllFields.toArray).foreach(y => println(y(1)._2.getClass))

//        println(x._2.size)
        val longListFields = x._2
          .filter { y =>
            val fields = y.getAllFields.toArray
            fields(1)._2.isInstanceOf[Vector[Long]]
          }
          .map { y =>
            val fields = y.getAllFields.toArray
            val field = fields(1)._2.asInstanceOf[Vector[Long]]
            y.name + """ = Some((Array("""" + field.mkString("""","""") + """")))"""
          }
        val longFields = x._2
          .filter { y =>
            val fields = y.getAllFields.toArray
            fields(1)._2.isInstanceOf[Long]
          }
          .map { y =>
            val fields = y.getAllFields.toArray
            val field = fields(1)._2.asInstanceOf[Long]
            y.name + """ = Some(("""" + field.toInt + """"))""" 
          }
        val stringFields = x._2
          .filter { y =>
            val fields = y.getAllFields.toArray
            fields(1)._2.isInstanceOf[String]
          }
          .map { y =>
            val fields = y.getAllFields.toArray
            val field = fields(1)._2.asInstanceOf[String]
            y.name + """ = Some(("""" + field + """"))"""
          }
        val tensorProtoFields = x._2
          .filter { y =>
            val fields = y.getAllFields.toArray
            fields(1)._2.isInstanceOf[TensorProto]
          }
          .map { y =>
            val fields = y.getAllFields.toArray
            val field = ParamsMap.onnxTensorProtoToArray[VV](
              fields(1)._2.asInstanceOf[TensorProto])
            y.name + " = Some((Array(" + field.mkString(",") + ")))"
          }
       
        val opName = x._1._1._2
        val nodeName = x._1._2(0) 
        "      node" + nodeName + " <- " + opName + "." + opName + "1" + "[VV]" +
        "(" +
        """"""" + nodeName + """", """ + //assumes > 0 args
          nodesOrParams.mkString(",") +
          (if (tensorProtoFields.size > 0) "," else "") +
          tensorProtoFields.mkString(",") +
          (if (longListFields.size > 0) "," else "") +
          longListFields.mkString(",") +
          (if (stringFields.size > 0) "," else "") +
          stringFields.mkString(",") +
          (if (longFields.size > 0) "," else "") +
          longFields.mkString(",") +
          ")\n"
      }
      .mkString("") +
    "    } yield (" +
    outputs.map(x => "node" + x.name).mkString(",") +
    ")\n" +
    "}\n"
  }
//pw.write("for {\n")

  def generate() = {
//    println(fullSource[Float])
    //Seems to not catch some things it should
    val onnxSource = fullSource[Float].parse[Source].get

    Files.write(path, onnxSource.syntax.getBytes("UTF-8"));
  }

  generate()

}
