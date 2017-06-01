#!/bin/bash

NAME="jira-config"

kubectl delete configmap $NAME
kubectl create configmap $NAME --from-file=../conf