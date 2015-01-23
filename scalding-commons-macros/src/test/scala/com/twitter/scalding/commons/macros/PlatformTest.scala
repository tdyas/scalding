/*
Copyright 2014 Twitter, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/
package com.twitter.scalding.commons.macros

import com.twitter.scalding._

import org.scalatest.{ Matchers, WordSpec }

import com.twitter.scalding.platform.{ HadoopSharedPlatformTest, HadoopPlatformJobTest }
import com.twitter.chill.thrift.TBaseSerializer
import com.twitter.chill.{ IKryoRegistrar, ReflectingRegistrar, ReflectingDefaultRegistrar, ScalaKryoInstantiator }
import com.twitter.chill.java.IterableRegistrar
import org.apache.thrift.TBase
import com.twitter.chill.config.{ ConfiguredInstantiator, ScalaAnyRefMapConfig }
import com.twitter.scalding.commons.macros.impl.{ ScroogeTProtocolOrderedSerializationImpl, TBaseOrderedSerializationImpl, ScroogeInternalOrderedSerializationImpl }
import com.twitter.scalding.commons.thrift.{ ScroogeTProtocolOrderedSerialization, TBaseOrderedSerialization }
import com.twitter.scalding.serialization.OrderedSerialization
import scala.language.experimental.{ macros => sMacros }
import com.twitter.scrooge.ThriftStruct
import com.twitter.scalding.commons.macros.scalathrift._
import org.scalacheck.Arbitrary

class ThriftCompareJob(args: Args) extends Job(args) {
  val tp = TypedPipe.from((0 until 100).map { idx =>
    new TestThriftStructure("asdf", idx % 10)
  })
  tp.map(_ -> 1L).sumByKey.map {
    case (k, v) =>
      (k.toString, v)
  }.write(TypedTsv[(String, Long)]("output"))
}

class CompareJob[T: OrderedSerialization](in: Iterable[T], args: Args) extends Job(args) {
  TypedPipe.from(in).flatMap{ i =>
    (0 until 1).map (_ => i)
  }.map(_ -> 1L).sumByKey.map {
    case (k, v) =>
      (k.hashCode, v)
  }.write(TypedTsv[(Int, Long)]("output"))
}
private[macros] trait InstanceProvider[T] {
  def g(idx: Int): T
}
class PlatformTest extends WordSpec with Matchers with HadoopSharedPlatformTest {
  org.apache.log4j.Logger.getLogger("org.apache.hadoop").setLevel(org.apache.log4j.Level.FATAL)
  org.apache.log4j.Logger.getLogger("org.mortbay").setLevel(org.apache.log4j.Level.FATAL)

  def toScroogeTProtocolOrderedSerialization[T <: ThriftStruct]: ScroogeTProtocolOrderedSerialization[T] = macro ScroogeTProtocolOrderedSerializationImpl[T]

  def toScroogeInternalOrderedSerialization[T]: OrderedSerialization[T] = macro ScroogeInternalOrderedSerializationImpl[T]

  implicit def toTBaseOrderedSerialization[T <: TBase[_, _]]: TBaseOrderedSerialization[T] = macro TBaseOrderedSerializationImpl[T]

  import ScroogeGenerators._

  implicit def arbitraryInstanceProvider[T: Arbitrary] = new InstanceProvider[T] {
    def g(idx: Int) = ScroogeGenerators.dataProvider[T](idx)
  }

  implicit def testThriftStructProvider = new InstanceProvider[TestThriftStructure] {
    def g(idx: Int) = new TestThriftStructure("asdf" + idx, idx)
  }

  def runCompareTest[T: OrderedSerialization](implicit iprov: InstanceProvider[T]) {
    val input = (0 until 10000).map { idx =>
      iprov.g(idx % 50)
    }

    HadoopPlatformJobTest(new CompareJob[T](input, _), cluster)
      .sink(TypedTsv[(Int, Long)]("output")) { out =>
        import ScroogeGenerators._
        val expected =
          input
            .groupBy(identity)
            .map{ case (k, v) => (k.hashCode, v.size) }

        out.toSet shouldBe expected.toSet
      }
      .run
  }

  "TBase Test" should {
    "Expected items should match: TestThriftStructure2" in {
      runCompareTest[TestThriftStructure]
    }
  }

  "ThriftStruct Test" should {

    "Expected items should match : Internal Serializer / TestStructdd" in {

      runCompareTest[TestStruct](toScroogeInternalOrderedSerialization[TestStruct], implicitly)
    }

    "Expected items should match : TProtocol / TestStruct" in {
      implicit def toScroogeInternalOrderedSerialization[T]: OrderedSerialization[T] = macro ScroogeInternalOrderedSerializationImpl[T]
      runCompareTest[TestStruct](toScroogeTProtocolOrderedSerialization[TestStruct], implicitly)
    }

    "Expected items should match : Internal Serializer / TestSets" in {
      implicit def toScroogeInternalOrderedSerialization[T]: OrderedSerialization[T] = macro ScroogeInternalOrderedSerializationImpl[T]

      runCompareTest[TestSets](toScroogeInternalOrderedSerialization[TestSets], implicitly)
    }

    "Expected items should match : TProtocol / TestSets" in {
      runCompareTest[TestSets](toScroogeTProtocolOrderedSerialization[TestSets], implicitly)
    }

    "Expected items should match : Internal Serializer / TestLists" in {
      implicit def toScroogeInternalOrderedSerialization[T]: OrderedSerialization[T] = macro ScroogeInternalOrderedSerializationImpl[T]

      runCompareTest[TestLists](toScroogeInternalOrderedSerialization[TestLists], implicitly)
    }

    "Expected items should match : TProtocol / TestLists" in {
      runCompareTest[TestLists](toScroogeTProtocolOrderedSerialization[TestLists], implicitly)
    }

    "Expected items should match : Internal Serializer /  TestMaps" in {
      implicit def toScroogeInternalOrderedSerialization[T]: OrderedSerialization[T] = macro ScroogeInternalOrderedSerializationImpl[T]
      runCompareTest[TestMaps](toScroogeInternalOrderedSerialization[TestMaps], implicitly)
    }

    "Expected items should match : Internal Serializer / TestUnionfsggffd" in {
      implicit def toScroogeInternalOrderedSerialization[T]: OrderedSerialization[T] = macro ScroogeInternalOrderedSerializationImpl[T]
      toScroogeInternalOrderedSerialization[TestStruct]
      runCompareTest[TestUnion](toScroogeInternalOrderedSerialization[TestUnion], arbitraryInstanceProvider[TestUnion])
    }

    "Expected items should match : Internal Serializer / TTestMaps" in {
      implicit def toScroogeInternalOrderedSerialization[T]: OrderedSerialization[T] = macro ScroogeInternalOrderedSerializationImpl[T]
      runCompareTest[TestMaps](toScroogeTProtocolOrderedSerialization[TestMaps], implicitly)
    }

    "Expected items should match : Internal Serializer / Enum" in {
      implicit def toScroogeInternalOrderedSerialization[T]: OrderedSerialization[T] = macro ScroogeInternalOrderedSerializationImpl[T]
      // Our scrooge one operates on thrift structs, not TEnums
      // runCompareTest[TestEnum](toScroogeTProtocolOrderedSerialization[TestEnum], implicitly)
      runCompareTest[TestEnum](toScroogeInternalOrderedSerialization[TestEnum], implicitly)
    }

    "Expected items should match : Internal Serializer / TestTypes" in {
      implicit def toScroogeInternalOrderedSerialization[T]: OrderedSerialization[T] = macro ScroogeInternalOrderedSerializationImpl[T]
      runCompareTest[TestTypes](toScroogeInternalOrderedSerialization[TestTypes], implicitly)
    }

    "Expected items should match : Internal Serializer / TestTypes2" in {
      implicit def toScroogeInternalOrderedSerialization[T]: OrderedSerialization[T] = macro ScroogeInternalOrderedSerializationImpl[T]
      runCompareTest[TestTypes](toScroogeInternalOrderedSerialization[TestTypes], implicitly)
    }

    "Expected items should match : TProtocol / TestTypes" in {
      runCompareTest[TestTypes](toScroogeTProtocolOrderedSerialization[TestTypes], implicitly)
    }

  }

}
