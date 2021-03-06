package com.huemulsolutions.bigdata.dataquality

class huemul_Profiling extends Serializable {
  var max_Col: String = null
  var min_Col: String = null
  var avg_Col: String = null
  var sum_Col: String = null
  var count_distinct_Col: Long = -1
  var count_all_Col: Long = -1
  var maxlen_Col: Integer = -1
  var minlen_Col: Integer = -1
  var count_empty: Long = -1
  var count_null: Long = -1
  var count_cero: Long = -1
}