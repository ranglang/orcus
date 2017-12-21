package orcus.codec

import org.apache.hadoop.hbase.client.Put
import org.apache.hadoop.hbase.util.Bytes
import shapeless._
import shapeless.labelled.FieldType

trait PutEncoder[A] {
  def apply(acc: Put, a: A): Option[Put]
}

object PutEncoder extends PutEncoder1 {

  def apply[A](implicit A: PutEncoder[A]): PutEncoder[A] = A
}

trait PutEncoder1 {

  implicit val hnilPutEncoder: PutEncoder[HNil] = new PutEncoder[HNil] {
    def apply(acc: Put, a: HNil): Option[Put] = Some(acc)
  }

  implicit def hlabelledConsPutEncoder[K <: Symbol, H, T <: HList](
      implicit
      K: Witness.Aux[K],
      H: Lazy[PutCFEncoder[H]],
      T: Lazy[PutEncoder[T]]
  ): PutEncoder[FieldType[K, H] :: T] = new PutEncoder[::[FieldType[K, H], T]] {
    def apply(acc: Put, a: FieldType[K, H] :: T): Option[Put] = a match {
      case h :: t =>
        for {
          hp <- H.value(acc, Bytes.toBytes(K.value.name), h)
          tp <- T.value(hp, t)
        } yield tp
    }
  }

  implicit def caseClassPutEncoder[A, R](
      implicit
      gen: LabelledGeneric.Aux[A, R],
      R: Lazy[PutEncoder[R]]
  ): PutEncoder[A] = new PutEncoder[A] {
    def apply(acc: Put, a: A): Option[Put] = {
      R.value(acc, gen.to(a))
    }
  }
}