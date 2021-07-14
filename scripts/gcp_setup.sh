#!/bin/bash

project=google.com:bin-wus-learning-center
project_num=

__usage() {
    echo "Usage: ./gcp_setup {logs|polkdadot}"
}

__setup_logs() {
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
}

__setup_polkdadot_data() {
    topic_data=polkadot-data
    sub_data=$topic_data-sub

    # create a pubsub topic
    gcloud pubsub topics create $topic_data

    # create subscription
    gcloud pubsub subscriptions create $sub_data --topic=$topic_data --topic-project=$project
}

__main() {
    if [ $# -eq 0 ]
    then
        __usage
    else
        case $1 in
            logs|log)
                __setup_logs
                ;;
            polkdadot|polkda|data)
                __setup_polkdadot_data
                ;;
            *)
                __usage
                ;;
        esac
    fi
}

__main $@