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
import org.bytedeco.javacpp.onnx.TensorProto
import collection.JavaConverters._
import spire.math.Number

import scala.reflect.ClassTag

object ONNXProgramGenerator extends App {
  val FS = true
  //Cut and pasted
  val useDotty = false
  val unionTypeOperator = (if(useDotty) " | " else " TypeOr ")
  //TODO: Get input types from first node
  val inputTypes = "T " + (if(useDotty) "<: " else ": ") + (if(useDotty) "" else "(UNil TypeOr ") +"Float16" + unionTypeOperator + "Float" + unionTypeOperator + "Double" + (if(useDotty) "" else ")#check") + ":Numeric:ClassTag:Field"

  val fileName = "super_resolution.onnx"
  val programName = fileName.stripSuffix(".onnx").capitalize + (if(FS) "Free" else "")
  val path = Paths.get("src/main/scala/" + programName + ".scala");

  val paramsMap = new ParamsMap(fileName)

  def fullSource[VV:spire.math.Numeric: ClassTag] = {
    val params = paramsMap.params[VV]
    val nodes = paramsMap.nodes[VV]
    val nodeInputs = paramsMap.nodeInputs[VV]
    val nodeOutputs = paramsMap.nodeOutputs
    val outputs = paramsMap.outputs[VV]
    val attributes = paramsMap.attributes
  //val sortedParamNames = params.keys.toSeq.sorted.map(x => "param_" + x)
    val ops = paramsMap.ops
    val distinctOps = ops.distinct

    val nodesInputsOpsAndOutputs = (nodeInputs zip ops) zip nodeOutputs

    "package org.emergentorder.onnx" + (if(FS) "Free" else "") + "\n\n" +
    (if(FS) 
      "import freestyle.free._\n" +
      "import freestyle.free.implicits._\n" +
      "import cats.free.{ Free, FreeApplicative } \n" +
      "import cats.implicits._ \n" +
      "import cats.effect.IO\n" +
      "import org.emergentorder.onnx._\n"
      else ""
      )  +
    (if(useDotty) "" else
      "import org.emergentorder.onnx.UnionType._\n"
    ) +
    "import scala.reflect.ClassTag\n" +
    "import spire.implicits._\n" +
    "import spire.math.UByte\n" +
    "import spire.math.UShort\n" +
    "import spire.math.Complex\n" +
    "import spire.algebra.Field\n" +
    "import spire.math.Numeric\n" +
    "import singleton.ops._\n" +
    "import scala.language.higherKinds\n\n" +
    (if(FS) "@module " else "") + "trait " + programName + " {\n" +
    distinctOps
      .map { x =>
        "  val " + x + (if(FS) "Free" else "") + ": " + x.capitalize + (if(FS) "Free" else "") + "\n"
      }
      .mkString("") +
    "  val dataSource: DataSource" + (if(FS) "Free" else "") + "\n" +
//    "  import cats.implicits._\n" +
    //Omit return type here for now
    "  def program[" + inputTypes + ", J <: XInt]" + (if(FS) ": FS.Seq[Tensor[T,J]] " else ": List[Tensor[T,J]] ") + " = \n" +
    //Body of program generated here
    "    for {\n" +
    //TODO: Assumes one output for now, enable multiple outputs for full computation graph
    "      node" +
    nodeInputs(0)(0) +
    " <- " + (if(FS) "" else "List(") + "dataSource.inputData" +(if(FS) "Free" else "") +"[T,J]" + (if(FS) "" else ")") + "\n" +
    params
      .map(x =>
        "      node" + x._1 + " <- "
          + (if(FS) "" else "List(") + " dataSource.getParams" + (if(FS) "Free" else "") + "[T,J](\"" + x._1 + "\")" + (if(FS) "" else ")" ) + "\n")
      .mkString("") +
    (nodesInputsOpsAndOutputs zip attributes)
      .map { x =>
        val nodesOrParams = x._1._1._1.map(y => "node" + y + """, """" + y + """"""")
        val nodesOrParamsRaw = x._1._1._1.map(y => "node" + y)
//        x._2.map(y => y.getAllFields.toArray).foreach(y => println(y(1)._2.getClass))

//        println(x._2.size)

          val longFields = x._2
          .filter { y => y.has_i
          }
          .map { y =>

            val field = y.i.asInstanceOf[Long]
            y.name.getString + """ = Some((""" + field.toInt + """))"""
          }

          val longListFields = x._2
          .filter { y =>
            val longListCount = y.ints_size
            val longListList = (0 until longListCount.toInt).map(z => y.ints(z)).toList
            !longListList.isEmpty  //|| longList(0).isInstanceOf[Long]
          }
          .map { y =>
            val longListCount = y.ints_size
            val longListList = (0 until longListCount.toInt).map(z => y.ints(z)).toList
            val field = longListList.toVector.asInstanceOf[Vector[Long]]
            y.name.getString + """ = Some((Seq(""" + field.mkString(",") + """)))""" 
          }
        val stringFields = x._2
          .filter { y =>
            val stringCount = y.strings_size
            val stringList = (0 until stringCount.toInt).map(z => y.strings(z)).toList
            !stringList.isEmpty //stringList(1).isInstanceOf[String]
          }
          .map { y =>
            val stringCount = y.strings_size
            val stringList = (0 until stringCount.toInt).map(z => y.strings(z)).toList
            val field = stringList.asInstanceOf[String]
            y.name.getString + """ = Some(Seq(""" + field + """))"""
          }
        val tensorProtoFields = x._2
          .filter { y =>
            val tensorCount = y.tensors_size
            val tensorList = (0 until tensorCount.toInt).map(z => y.tensors(z)).toList
            //fields(1)._2.isInstanceOf[TensorProto]
            !tensorList.isEmpty //tensorList(1).isInstanceOf[TensorProto]
          }
          .map { y =>
            val tensorCount = y.tensors_size
            val tensorList = (0 until tensorCount.toInt).map(z => y.tensors(z)).toList
            val field = paramsMap.onnxTensorProtoToArray[VV](
              tensorList.asInstanceOf[TensorProto])
            y.name.getString + " = Some((Array(" + field.mkString(",") + ")))"
          }
       
        val opName = x._1._1._2
        val nodeName = x._1._2(0) 
        //TODO: Select correct op version instead of 1
        //TODO:  ? Use cats IO Parallel . i.e. (name, age).parMapN { (name, age) => Person(name, age) }
        "      node" + nodeName + " <- " + (if(FS) ""
         // "(" + nodesOrParamsRaw.mkString(",") + ").parMap" + nodesOrParamsRaw.size + " {(" + nodesOrParamsRaw.mkString(",") + ")" + " => " 
          else "List(") + opName + (if(FS) "Free" else "") + "." + opName + "1" + (if(FS) "Free" else "")  + "" +
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
          ")" + (if(FS) ""
           // "}" 
            else ")") + "\n"
      }
      .mkString("") +
    "    } yield (" +
    outputs.map(x => "node" + x.name.getString).mkString(",") +
    ")\n" +
    "}\n"
  }
//pw.write("for {\n")

  def generate() = {
    println(fullSource[Int])
    //Seems to not catch some things it should
    val onnxSource = fullSource[Int].parse[Source].get

    Files.write(path, onnxSource.syntax.getBytes("UTF-8"));
  }

  generate()

}
