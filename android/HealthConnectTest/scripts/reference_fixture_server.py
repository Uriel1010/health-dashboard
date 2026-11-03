"""Local emulator-only nutrition fixture. Never contacts a real server."""
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from datetime import date, timedelta
import json

today=date.today()
goals=dict(caloriesKcal=2000,proteinG=150,carbsG=250,fatG=70,fiberG=30)
user=dict(id=1,username='demo',displayName='Demo account',isAdmin=False,isActive=True,mustChangePassword=False,
          currentNutritionDate=str(today),serverTimezone='Asia/Jerusalem',nutritionResetHour=0,nutritionGoals=goals)
def meal(identifier,title,kind,hour,calories,protein,carbs,fat,fiber):
    stamp=f'{today}T{hour:02}:12:00+02:00'
    return dict(id=identifier,deviceId='emulator-fixture',clientMealId=None,title=title,mealType=kind,
                eatenAt=stamp,localDate=str(today),nutritionDate=str(today),timezone='Asia/Jerusalem',
                caloriesKcal=calories,proteinG=protein,carbsG=carbs,fatG=fat,fiberG=fiber,
                sugarG=None,sodiumMg=None,notes=None,createdAt=stamp,updatedAt=stamp)
meals=[meal(1,'Oatmeal & Fruit','breakfast',8,412,24,74,12,6),meal(2,'Chicken Bowl','lunch',12,620,42,64,18,8),
       meal(3,'Salmon & Rice','dinner',19,710,50,48,28,10)]
points=[]
for offset in range(30,0,-1):
    value=[1740,1865,1810,1750,1845,1905,1853][(7-offset)%7]
    points.append(dict(date=str(today-timedelta(days=offset)),hasMeals=True,
                       totals=dict(caloriesKcal=value,proteinG=118,carbsG=212,fatG=62,fiberG=26)))
statistics=dict(fromDate=points[0]['date'],toDate=points[-1]['date'],currentNutritionDate=str(today),
                serverTimezone='Asia/Jerusalem',nutritionResetHour=0,points=points,windows=[
    dict(days=length,fromDate=points[-length]['date'],toDate=points[-1]['date'],loggedDays=length,
         average={key:sum(point['totals'][key] for point in points[-length:])/length for key in goals},
         coverage={key:length for key in goals}) for length in (7,14,30)])
failure=False
class Handler(BaseHTTPRequestHandler):
    def reply(self,body,status=200):
        self.send_response(status); self.send_header('Content-Type','application/json')
        self.send_header('Set-Cookie','health_session=synthetic-emulator-session; Path=/; HttpOnly')
        self.end_headers(); self.wfile.write(json.dumps(body).encode())
    def do_GET(self):
        path=self.path.split('?')[0]
        if path=='/fixture/info': self.reply({'fixture':'health-companion-reference-fixture'})
        elif path.startswith('/api/v1/push/meals/'):
            item=next((item for item in meals if item['id']==int(path.rsplit('/',1)[-1])),None)
            self.reply(item if item else {'detail':'Meal unavailable'},200 if item else 404)
        elif path.endswith('/auth/me'): self.reply(user)
        elif path.endswith('/nutrition/meals'): self.reply({'detail':'Synthetic connection failure'},503) if failure else self.reply({'meals':meals})
        elif path.endswith('/nutrition/statistics'): self.reply(statistics)
        elif path=='/health': self.reply({'status':'ok','database':'ok'})
        else: self.reply({'status':'ok'})
    def do_POST(self):
        global failure
        body=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))) or b'{}')
        if self.path=='/fixture/fail': failure=body.get('fail',False); self.reply({'ok':True})
        elif self.path=='/api/v1/push/installations': self.reply({'binding':'a'*48,'available':True})
        elif self.path.endswith('/auth/login'): self.reply(user)
        elif self.path.endswith('/nutrition/meals'):
            new=meal(max(item['id'] for item in meals)+1,body['title'],body['mealType'],12,body['caloriesKcal'],body['proteinG'],body['carbsG'],body['fatG'],None)
            meals.insert(0,new); self.reply(new,201)
        else: self.reply({'status':'ok','message':'Synthetic upload accepted'})
    def do_PATCH(self):
        body=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))) or b'{}')
        if 'nutritionGoals' in body: goals.update(body['nutritionGoals'])
        self.reply(user)
    def do_DELETE(self):
        identifier=int(self.path.rsplit('/',1)[-1]); meals[:]=[item for item in meals if item['id']!=identifier]; self.reply({'ok':True})
    def log_message(self,*args): pass
print('Synthetic emulator server listening on 127.0.0.1:18016',flush=True)
ThreadingHTTPServer(('127.0.0.1',18016),Handler).serve_forever()
