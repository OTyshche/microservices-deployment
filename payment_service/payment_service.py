# payment_service.py
from flask import Flask, request, jsonify

app = Flask(__name__)

@app.route('/process', methods=['POST'])
def process_payment():
    data = request.json
    user_id = data.get('userId')
    amount = data.get('amount')

    if not user_id or not amount:
        return jsonify({"error": "Требуются userId и amount"}), 400

    print(f"Имитация оплаты для пользователя {user_id}: {amount} USD")
    # В реальном приложении здесь была бы логика взаимодействия с платежным шлюзом
    return jsonify({"status": "Оплата успешна", "userId": user_id, "amount": amount}), 200

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8004)