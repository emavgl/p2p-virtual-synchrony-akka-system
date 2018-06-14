import sys
import argparse
import subprocess

parser = argparse.ArgumentParser(description='Run a new akka node')
parser.add_argument("port", help="port (es. 10000)")
parser.add_argument("host", help="host (es. 127.0.0.1)")
args = parser.parse_args()

with open('base.conf') as bconf:
  base = bconf.read()

host = str(args.host)
port = str(args.port)

new_node_config = base.format(host, port)

path = './src/main/resources/conf_{}.conf'.format(port)
filename = 'conf_{}.conf'.format(port)

with open(path, 'w') as w:
  w.write(new_node_config)

subprocess.run("gradle run -Dconfig=" + filename, shell=True, check=True)