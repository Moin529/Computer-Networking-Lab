import matplotlib.pyplot as plt

def read_csv(filename):
    rounds, cwnd, ssthresh = [], [], []
    with open(filename, "r") as f:
        lines = f.readlines()[1:]  # skip header
        for line in lines:
            r, c, s = line.strip().split(",")
            rounds.append(int(r))
            cwnd.append(int(c))
            ssthresh.append(int(s))
    return rounds, cwnd, ssthresh

# Read logs
t_round, t_cwnd, t_ssthresh = read_csv("tahoe_log.csv")
r_round, r_cwnd, r_ssthresh = read_csv("reno_log.csv")

plt.figure(figsize=(14, 7))

# TCP Tahoe
plt.plot(t_round, t_cwnd, marker='o', label="TCP Tahoe - cwnd")
plt.plot(t_round, t_ssthresh, linestyle='--', label="TCP Tahoe - ssthresh")

# TCP Reno
plt.plot(r_round, r_cwnd, marker='s', label="TCP Reno - cwnd")
plt.plot(r_round, r_ssthresh, linestyle='--', label="TCP Reno - ssthresh")

# Mark ssthresh changes
for i in range(1, len(t_ssthresh)):
    if t_ssthresh[i] != t_ssthresh[i - 1]:
        plt.scatter(t_round[i], t_ssthresh[i], marker='x', s=80, label="Tahoe ssthresh change")

for i in range(1, len(r_ssthresh)):
    if r_ssthresh[i] != r_ssthresh[i - 1]:
        plt.scatter(r_round[i], r_ssthresh[i], marker='x', s=80, label="Reno ssthresh change")

plt.title("TCP Tahoe vs TCP Reno — Congestion Window (cwnd) Growth Over Transmission Rounds", fontsize=16)
plt.xlabel("Transmission Round", fontsize=14)
plt.ylabel("Congestion Window Size (cwnd)", fontsize=14)
plt.grid(True, linestyle="--", alpha=0.6)
plt.legend()
plt.tight_layout()

plt.savefig("tahoe_vs_reno_plot.png", dpi=300)
plt.show()
