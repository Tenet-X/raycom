#!/bin/bash

project=google.com:bin-wus-learning-center
topic=polkadot-eco-logs
subscription=polkadot-eco-logs4k8es
sink=polkadot-eco-logs2pubsub

# create a pubsub topic
gcloud pubsub topics create $topic

# create subscription
gcloud pubsub subscriptions create $subscription --topic=$topic --topic-project=$project

# create a stackdriver sink (pubsub)
gcloud logging sinks create $sink pubsub.googleapis.com/projects/$project/topics/$topic \
    --log-filter='resource.type="k8s_container" AND resource.labels.project_id="local-alignment-284902" AND resource.labels.location="asia-east1" AND resource.labels.cluster_name="tenetx-tw" AND resource.labels.namespace_name="default" AND labels.k8s-pod/app="polkdot-phala-node"'
    # --log-filter='resource.type="http_load_balancer"'

# add service account used by stackdriver for pubsub topic
logging_sa=`gcloud logging sinks describe $sink | grep "writerIdentity" | cut -d ' ' -f 2`
gcloud pubsub topics add-iam-policy-binding $topic \
    --member $logging_sa \
    --role roles/pubsub.publisher
