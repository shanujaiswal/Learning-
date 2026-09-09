// New keyword 
function a(firstName, age){
    this.firstName = firstName;
    this.age = age ;
}
a.prototype.about = function(){
    console.log(this.firstName, this.age);
}
const user1 = new a("Shanu", 24)
console.log(user1)

 