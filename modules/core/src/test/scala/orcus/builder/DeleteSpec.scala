package orcus.builder

import java.{util => ju}

import org.apache.hadoop.hbase.client.{Durability, Delete => HDelete}
import org.apache.hadoop.hbase.security.access.Permission
import org.apache.hadoop.hbase.security.visibility.CellVisibility
import org.apache.hadoop.hbase.util.Bytes
import org.mockito.Mockito._

import scala.collection.JavaConverters._

class DeleteSpec extends BuilderSpec {

  describe("withACL") {
    it("should call setACL") {
      val m = spy(new HDelete(rowkey))
      val v = new Permission
      new Delete(m).withACL("1", v)
      verify(m).setACL("1", v)
    }
  }
  describe("withCellVisibility") {
    it("should call setCellVisibility") {
      val m = spy(new HDelete(rowkey))
      val v = new CellVisibility("!a")
      new Delete(m).withCellVisibility(v)
      verify(m).setCellVisibility(v)
    }
  }
  describe("withClusterIds") {

    it("should call setClusterIds") {
      val m = spy(new HDelete(rowkey))
      val v = ju.Arrays.asList(ju.UUID.randomUUID(), ju.UUID.randomUUID())
      new Delete(m).withClusterIds(v.asScala)
      verify(m).setClusterIds(v)
    }
  }
  describe("withDurability") {
    it("should call setDurability") {
      val m = spy(new HDelete(rowkey))
      new Delete(m).withDurability(Durability.ASYNC_WAL)
      verify(m).setDurability(Durability.ASYNC_WAL)
    }
  }
  describe("withId") {
    it("should call setId") {
      val m = spy(new HDelete(rowkey))
      new Delete(m).withId("1")
      verify(m).setId("1")
    }
  }
  describe("withAttribute") {
    it("should call setAttribute") {
      val m = spy(new HDelete(rowkey))
      new Delete(m).withAttribute("n", Bytes.toBytes("v"))
      verify(m).setAttribute("n", Bytes.toBytes("v"))
    }
  }
  describe("withFamily") {
    it("should call addFamily") {
      val m  = spy(new HDelete(rowkey))
      val cf = Bytes.toBytes("a")
      new Delete(m).withFamily(cf)
      verify(m).addFamily(cf)
    }
  }
  describe("withFamilyTo") {
    it("should call addFamily") {
      val m  = spy(new HDelete(rowkey))
      val cf = Bytes.toBytes("a")
      new Delete(m).withFamilyTo(cf, 10)
      verify(m).addFamily(cf, 10)
    }
  }
  describe("withFamilyVersion") {
    it("should call addFamily") {
      val m  = spy(new HDelete(rowkey))
      val cf = Bytes.toBytes("a")
      new Delete(m).withFamilyVersion(cf, 10)
      verify(m).addFamilyVersion(cf, 10)
    }
  }
  describe("withColumnLatest") {
    it("should call addColumn") {
      val m  = spy(new HDelete(rowkey))
      val cf = Bytes.toBytes("a")
      new Delete(m).withColumnLatest(cf, 10)
      verify(m).addColumn(cf, Bytes.toBytes(10))
    }
  }
  describe("withColumnVersion") {
    it("should call addColumn") {
      val m  = spy(new HDelete(rowkey))
      val cf = Bytes.toBytes("a")
      new Delete(m).withColumnVersion(cf, 10, 10)
      verify(m).addColumn(cf, Bytes.toBytes(10), 10)
    }
  }
  describe("withColumns") {
    it("should call addColumns") {
      val m  = spy(new HDelete(rowkey))
      val cf = Bytes.toBytes("a")
      new Delete(m).withColumns(cf, 10)
      verify(m).addColumns(cf, Bytes.toBytes(10))
    }
  }
  describe("withColumnsVersion") {
    it("should call addColumns") {
      val m  = spy(new HDelete(rowkey))
      val cf = Bytes.toBytes("a")
      new Delete(m).withColumnsVersion(cf, 10, 10)
      verify(m).addColumns(cf, Bytes.toBytes(10), 10)
    }
  }
}