# Why Storage Needs Its Own Abstraction

--> Pods are ephemeral -- when a pod is rescheduled (crashed node, rolling update), it may start on an entirely different physical machine. A stateful workload (database, file store) needs its data to survive that move.
--> Kubernetes separates the REQUEST for storage from the actual underlying storage infrastructure, so application manifests don't need to know if they're running on AWS EBS, GCP Persistent Disk, or on-prem storage.

# PersistentVolume (PV) and PersistentVolumeClaim (PVC)

--> PersistentVolume (PV) -- a piece of actual storage provisioned in the cluster (by an admin, or dynamically) -- exists independently of any pod's lifecycle.
--> PersistentVolumeClaim (PVC) -- a request FOR storage made by a pod ("I need 10Gi, ReadWriteOnce access") -- Kubernetes binds it to a matching PV. Pods reference the PVC, never the PV directly.

```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: db-pvc
spec:
  accessModes:
    - ReadWriteOnce          # Mountable read-write by a single node at a time
  resources:
    requests:
      storage: 10Gi
  storageClassName: standard
```

```yaml
# Referencing the PVC inside a pod spec
volumes:
  - name: db-storage
    persistentVolumeClaim:
      claimName: db-pvc
containers:
  - name: db
    volumeMounts:
      - mountPath: /var/lib/postgresql/data
        name: db-storage
```

# Access Modes

--> ReadWriteOnce (RWO) -- mounted read-write by a single node (most block storage -- AWS EBS, GCP PD). The common case for a single-instance database.
--> ReadOnlyMany (ROX) -- mounted read-only by many nodes simultaneously.
--> ReadWriteMany (RWX) -- mounted read-write by many nodes at once -- requires network filesystem-backed storage (NFS, EFS, Azure Files); most cloud block storage does NOT support this.

# StorageClasses -- Dynamic Provisioning

--> Without a StorageClass, an admin must manually pre-create PVs before any PVC can bind -- doesn't scale.
--> A StorageClass defines HOW to dynamically provision storage on demand -- when a PVC requests it, the cluster automatically creates a matching PV behind the scenes (e.g. provisioning a new AWS EBS volume).

```yaml
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: fast-ssd
provisioner: ebs.csi.aws.com
parameters:
  type: gp3
reclaimPolicy: Delete       # What happens to the underlying volume when the PVC is deleted
volumeBindingMode: WaitForFirstConsumer
```

--> `reclaimPolicy: Retain` vs `Delete` -- Retain keeps the underlying cloud volume (and its data) even after the PVC is deleted, useful for databases where accidental deletion shouldn't destroy data; Delete cleans it up automatically, useful for disposable/scratch storage.

# StatefulSets + PVCs -- Stable Storage Per Replica

--> A StatefulSet's `volumeClaimTemplates` gives EACH replica its own PVC, automatically named/matched to that specific pod (`db-0` always gets `db-0`'s volume, even after a restart or reschedule) -- this is what makes running a genuinely stateful clustered database (PostgreSQL replicas, Kafka brokers, Elasticsearch nodes) possible on Kubernetes.

```yaml
volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: fast-ssd
      resources:
        requests:
          storage: 20Gi
```
