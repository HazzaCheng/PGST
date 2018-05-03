# ParallelGST

ParallelGST (**Parallel** **G**eneralized **S**uffix **T**ree) is an efficient and scalable generalized suffix tree construction algorithm on distributed data-parallel platforms. In addition, ParallelGST is built on the widely-used distributed data-parallel platform Apache Spark.

# Environment

- Apache Spark: Spark 1.6
- Apache HDFS: ParallelGST uses HDFS as the distributed file system. The HDFS version is 2.6.5.
- Java: The JDK version is 1.8
- Scala: The Scala SDK version is 2.10.4

# Compile

ParallelGST is built using Apache Maven.
To build ParallelGST, Run `mvn scala:compile compile package` in the root directory.

# Run

The entry of ParallelGST is defined in the scala class `com.wanghuanming.suffixtree.App`.

Run ParallelGST with the following command:
```
    spark-submit \
        -v \
        --master [SPARK_MASTER_ADDRESS] \
        --name "ParallelGST" \
        --class "com.wanghuanming.suffixtree.App" \
        --executor-cores 4 \
        --executor-memory 20G \
        --driver-memory 20G \
        GST.jar \
        <Input path> \
        <Output path> \
        <Temporary path> \
```

The run command contains the following parameters:

- `<Input path>`: The input data path on HDFS or local file system.
- `<Output path>`: The output data path on HDFS or local file system.
- `<Temporary path>`: The tempoaray data path on HDFS or local file system.
