FROM debian:bookworm-slim
RUN apt-get update && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends openssh-server ca-certificates bash python3 python3-pip python3-venv python3-docx python3-reportlab git curl nodejs npm make gcc g++ && rm -rf /var/lib/apt/lists/* && mkdir -p /run/sshd /workspace && useradd -m -s /bin/bash nemesis && chown nemesis:nemesis /workspace
RUN printf '%s\n' 'Port 22' 'ListenAddress 0.0.0.0' 'HostKey /etc/ssh/ssh_host_ed25519_key' 'PasswordAuthentication yes' 'KbdInteractiveAuthentication no' 'UsePAM no' 'PermitRootLogin no' 'AllowUsers nemesis' >> /etc/ssh/sshd_config
WORKDIR /workspace
CMD ["/bin/bash", "-c", "echo nemesis:$NEMESIS_TEST_PASSWORD | chpasswd; chown nemesis:nemesis /workspace; exec /usr/sbin/sshd -D -e"]
