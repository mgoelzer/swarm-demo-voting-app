from flask import Flask
from flask import render_template
from flask import request
from flask import make_response
from utils import connect_to_redis
import os
import socket
import random
import json
import time

option_a = os.getenv('OPTION_A', "Cats")
option_b = os.getenv('OPTION_B', "Dogs")
hostname = socket.gethostname()

db_server = "redis%s" % os.environ['FRONTEND_NUM']
redis = connect_to_redis(db_server)
app = Flask(__name__)

@app.route("/env", methods=['GET'])
def dump_env():
    s = ''
    for key in os.environ.keys():
        s = "%s%30s=%s\n" % (s, key,os.environ[key])
    resp = make_response(render_template(
	    'env.html',
	    s=s
    ))
    return resp

@app.route("/", methods=['POST','GET'])
def index():
    voter_id = request.cookies.get('voter_id')
    if not voter_id:
        voter_id = hex(random.getrandbits(64))[2:-1]

    vote = None

    if request.method == 'POST':
        vote = request.form['vote']
	epoch_time_ms = long(time.time()*1000)
	data = json.dumps({'voter_id': voter_id, 'vote': vote, 'ts': epoch_time_ms})
        redis.rpush('votes', data)

    resp = make_response(render_template(
        'index.html',
        option_a=option_a,
        option_b=option_b,
        hostname=hostname,
	node="frontend%s" % os.environ['FRONTEND_NUM'],
        vote=vote,
    ))
    resp.set_cookie('voter_id', voter_id)
    return resp


if __name__ == "__main__":
	app.run(host='0.0.0.0', port=80, debug=True)
