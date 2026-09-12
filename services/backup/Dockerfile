FROM restic/restic:0.19.1

RUN apk add --no-cache bash busybox-extras coreutils curl jq util-linux

COPY --chmod=755 backup-manager.sh /usr/local/bin/backup-manager
COPY backup-excludes.txt /etc/mmucraft/backup-excludes.txt

ENTRYPOINT ["/usr/local/bin/backup-manager"]
CMD ["run"]
