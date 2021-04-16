pwd := $(shell pwd)
ipaddr := $(shell hostname -I | cut -d ' ' -f 1)
region := asia-east1
workerType := e2-standard-2
workerZone := b
project := g-hash-community
job := polkadot-data-dev
numWorkers := 2
maxWorkers := 20
eshost := https://k8es.ingest.bindiego.com
esuser := polkadot-data-ingest
espass := changeme
esindex := polkadot-ingest
esBatchSize := 2000
esBatchBytes := 10485760
esNumThread := 2
blkIdx := polkadata-blk-ingest
txIdx := polkadata-tx-ingest
bqblk := gs://polkadot-tw-oss/bqschema/polkadata-blk.json
bqtx := gs://polkadot-tw-oss/bqschema/polkadata-tx.json
bqblktbl := g-hash-community:polkadot.block
bqtxtbl := g-hash-community:polkadot.transaction
logssub := polkadot-logs-sub
datasub := polkadot-data-sub

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
        --maxNumWorkers=$(maxWorkers) \
        --workerMachineType=$(workerType) \
        --diskSizeGb=32 \
        --numWorkers=$(numWorkers) \
        --tempLocation=gs://polkadot-tw-oss/tmp/ \
        --gcpTempLocation=gs://polkadot-tw-oss/tmp/gcp/ \
        --gcsTempLocation=gs://polkadot-tw-oss/tmp/gcs/ \
        --stagingLocation=gs://polkadot-tw-oss/staging/ \
        --runner=DataflowRunner \
        --topic=projects/$(project)/topics/polkadot-eco-logs \
        --subscription=projects/$(project)/subscriptions/$(logssub) \
        --polkadatasub=projects/$(project)/subscriptions/$(datasub) \
        --numShards=1 \
        --windowSize=6s \
        --allowedLateness=8s \
        --earlyFiringPeriod=2s \
        --lateFiringCount=1 \
        --filenamePrefix=polkadot. \
        --outputDir=gs://polkadot-tw-oss/polkadot/out/ \
        --errOutputDir=gs://polkadot-tw-oss/polkadot/out/err/ \
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
        --maxNumWorkers=$(maxWorkers) \
        --workerMachineType=$(workerType) \
        --diskSizeGb=32 \
        --numWorkers=$(numWorkers) \
        --tempLocation=gs://polkadot-tw-oss/tmp/ \
        --gcpTempLocation=gs://polkadot-tw-oss/tmp/gcp/ \
        --gcsTempLocation=gs://polkadot-tw-oss/tmp/gcs/ \
        --stagingLocation=gs://polkadot-tw-oss/staging/ \
        --runner=DataflowRunner \
        --topic=projects/$(project)/topics/polkadot-eco-logs \
        --subscription=projects/$(project)/subscriptions/$(logssub) \
        --polkadatasub=projects/$(project)/subscriptions/$(datasub) \
        --numShards=1 \
        --windowSize=6s \
        --allowedLateness=8s \
        --earlyFiringPeriod=2s \
        --lateFiringCount=1 \
        --filenamePrefix=polkadot. \
        --outputDir=gs://polkadot-tw-oss/polkadot/out/ \
        --errOutputDir=gs://polkadot-tw-oss/polkadot/out/err/ \
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
        --region=$(region) \
        --workerZone=$(region)-$(workerZone)"

cancel:
	@gcloud dataflow jobs cancel $(job) --region=$(region)

drain:
	@gcloud dataflow jobs drain $(job) --region=$(region)

.PHONY: df dfup cancel drain baschema
