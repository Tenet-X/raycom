pwd := $(shell pwd)
ipaddr := $(shell hostname -I | cut -d ' ' -f 1)
region := asia-east1
project := local-alignment-284902
job := polkadot-data
eshost := https://k8es.ingest.bindiego.com
esuser := polkadot-data-ingest
espass := changeme
esindex := polkadot-ingest
esBatchSize := 2000
esBatchBytes := 10485760
esNumThread := 2

dfup:
	@mvn -Pdataflow-runner compile exec:java \
        -Dexec.mainClass=bindiego.BindiegoStreaming \
        -Dexec.cleanupDaemonThreads=false \
        -Dexec.args="--project=$(project) \
        --streaming=true \
        --autoscalingAlgorithm=THROUGHPUT_BASED \
        --maxNumWorkers=20 \
        --workerMachineType=n1-standard-2 \
        --diskSizeGb=64 \
        --numWorkers=3 \
        --tempLocation=gs://polkadot-tw/tmp/ \
        --gcpTempLocation=gs://polkadot-tw/tmp/gcp/ \
        --gcsTempLocation=gs://polkadot-tw/tmp/gcs/ \
        --stagingLocation=gs://polkadot-tw/staging/ \
        --runner=DataflowRunner \
        --topic=projects/$(project)/topics/polkadot-eco-logs \
        --subscription=projects/$(project)/subscriptions/polkadot-eco-logs4k8es \
        --numShards=1 \
        --windowSize=6s \
        --allowedLateness=8s \
        --earlyFiringPeriod=2s \
        --lateFiringCount=1 \
        --filenamePrefix=polkadot. \
        --outputDir=gs://polkadot-tw/polkadot/out/ \
        --errOutputDir=gs://polkadot-tw/polkadot/out/err/ \
        --esHost=$(eshost) \
        --esUser=$(esuser) \
        --esPass=$(espass) \
        --esIndex=$(esindex) \
        --esMaxBatchSize=$(esBatchSize) \
        --esMaxBatchBytes=$(esBatchBytes) \
        --esNumThread=$(esNumThread) \
        --defaultWorkerLogLevel=INFO \
        --jobName=$(job) \
        --update \
        --region=$(region)"

df:
	@mvn -Pdataflow-runner compile exec:java \
        -Dexec.mainClass=bindiego.BindiegoStreaming \
        -Dexec.cleanupDaemonThreads=false \
        -Dexec.args="--project=$(project) \
        --streaming=true \
        --autoscalingAlgorithm=THROUGHPUT_BASED \
        --maxNumWorkers=20 \
        --workerMachineType=n1-standard-2 \
        --diskSizeGb=64 \
        --numWorkers=3 \
        --tempLocation=gs://polkadot-tw/tmp/ \
        --gcpTempLocation=gs://polkadot-tw/tmp/gcp/ \
        --gcsTempLocation=gs://polkadot-tw/tmp/gcs/ \
        --stagingLocation=gs://polkadot-tw/staging/ \
        --runner=DataflowRunner \
        --topic=projects/$(project)/topics/polkadot-eco-logs \
        --subscription=projects/$(project)/subscriptions/polkadot-eco-logs4k8es \
        --numShards=1 \
        --windowSize=6s \
        --allowedLateness=8s \
        --earlyFiringPeriod=2s \
        --lateFiringCount=1 \
        --filenamePrefix=polkadot. \
        --outputDir=gs://polkadot-tw/polkadot/out/ \
        --errOutputDir=gs://polkadot-tw/polkadot/out/err/ \
        --esHost=$(eshost) \
        --esUser=$(esuser) \
        --esPass=$(espass) \
        --esIndex=$(esindex) \
        --esMaxBatchSize=$(esBatchSize) \
        --esMaxBatchBytes=$(esBatchBytes) \
        --esNumThread=$(esNumThread) \
        --defaultWorkerLogLevel=INFO \
        --jobName=$(job) \
        --region=$(region)"

cancel:
	@gcloud dataflow jobs cancel $(job) --region=$(region)

drain:
	@gcloud dataflow jobs drain $(job) --region=$(region)

.PHONY: df dfup cancel drain
