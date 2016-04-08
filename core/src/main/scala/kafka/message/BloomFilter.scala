package kafka.message


trait BloomFilter[T] {
  def contains(element: T): Boolean
  def add(element: T): Unit
}
