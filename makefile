pwd := $(shell pwd)
ipaddr := $(shell hostname -I | cut -d ' ' -f 1)
region := asia-east1
workerType := e2-standard-2
workerZone := b
project := local-alignment-284902
job := polkadot-data
eshost := https://k8es.ingest.bindiego.com
esuser := polkadot-data-ingest
espass := changeme
esindex := polkadot-ingest
esBatchSize := 2000
esBatchBytes := 10485760
esNumThread := 2
blkIdx := polkadata-blk-ingest
txIdx := polkadata-tx-ingest
bqblk := gs://polkadot-tw/bqschema/polkadata-blk.json
bqtx := gs://polkadot-tw/bqschema/polkadata-tx.json
bqblktbl := local-alignment-284902:polkadot.block
bqtxtbl := local-alignment-284902:polkadot.transaction

bqschema:
	@gsutil -m cp schemas/polkadata-blk.json $(bqblk)
	@gsutil -m cp schemas/polkadata-tx.json $(bqtx)

dfup: bqschema
	@mvn -Pdataflow-runner compile exec:java \
        -Dexec.mainClass=bindiego.BindiegoStreaming \
        -Dexec.cleanupDaemonThreads=false \
        -Dexec.args="--project=$(project) \
        --streaming=true \
        --autoscalingAlgorithm=THROUGHPUT_BASED \
        --maxNumWorkers=20 \
        --workerMachineType=$(workerType) \
        --diskSizeGb=64 \
        --numWorkers=3 \
        --tempLocation=gs://polkadot-tw/tmp/ \
        --gcpTempLocation=gs://polkadot-tw/tmp/gcp/ \
        --gcsTempLocation=gs://polkadot-tw/tmp/gcs/ \
        --stagingLocation=gs://polkadot-tw/staging/ \
        --runner=DataflowRunner \
        --topic=projects/$(project)/topics/polkadot-eco-logs \
        --subscription=projects/$(project)/subscriptions/polkadot-eco-logs4k8es \
        --polkadatasub=projects/$(project)/subscriptions/ok-polkadot-sub \
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
        --blkIdx=$(blkIdx) \
        --txIdx=$(txIdx) \
        --bqBlk=$(bqblk) \
        --bqTx=$(bqtx) \
        --bqBlkTbl=$(bqblktbl) \
        --bqTxTbl=$(bqtxtbl) \
        --defaultWorkerLogLevel=INFO \
        --jobName=$(job) \
        --update \
        --region=$(region) \
        --workerZone=$(region)-$(workerZone)"

df: bqschema
	@mvn -Pdataflow-runner compile exec:java \
        -Dexec.mainClass=bindiego.BindiegoStreaming \
        -Dexec.cleanupDaemonThreads=false \
        -Dexec.args="--project=$(project) \
        --streaming=true \
        --autoscalingAlgorithm=THROUGHPUT_BASED \
        --maxNumWorkers=20 \
        --workerMachineType=$(workerType) \
        --diskSizeGb=64 \
        --numWorkers=3 \
        --tempLocation=gs://polkadot-tw/tmp/ \
        --gcpTempLocation=gs://polkadot-tw/tmp/gcp/ \
        --gcsTempLocation=gs://polkadot-tw/tmp/gcs/ \
        --stagingLocation=gs://polkadot-tw/staging/ \
        --runner=DataflowRunner \
        --topic=projects/$(project)/topics/polkadot-eco-logs \
        --subscription=projects/$(project)/subscriptions/polkadot-eco-logs4k8es \
        --polkadatasub=projects/$(project)/subscriptions/ok-polkadot-sub \
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
        --blkIdx=$(blkIdx) \
        --txIdx=$(txIdx) \
        --defaultWorkerLogLevel=INFO \
        --jobName=$(job) \
        --region=$(region) \
        --workerZone=$(region)-$(workerZone)"

cancel:
	@gcloud dataflow jobs cancel $(job) --region=$(region)

drain:
	@gcloud dataflow jobs drain $(job) --region=$(region)

.PHONY: df dfup cancel drain baschema
