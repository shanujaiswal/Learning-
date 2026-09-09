"""
08_fastapi_mini_app.py

Maps to Theory chapter:
    16 Web Frameworks Flask Django and FastAPI.md

Demonstrates a small, correct FastAPI app:
    - Pydantic models for request/response bodies
    - Path parameters and query parameters
    - A couple of routes (GET, POST) with proper status codes

Pip installs needed to RUN this file (not needed just to read it):
    pip install fastapi uvicorn

How to run:
    uvicorn "08_fastapi_mini_app:app" --reload

Then visit http://127.0.0.1:8000/docs for the interactive Swagger UI.

Note: FastAPI/uvicorn are not part of the standard library, so importing this
file without them installed will raise ModuleNotFoundError -- that's expected
until you `pip install fastapi uvicorn`. The syntax itself is fully correct.
"""

from __future__ import annotations
from typing import Optional

from fastapi import FastAPI, HTTPException, Query
from pydantic import BaseModel, Field

app = FastAPI(title="Mini Student API", version="1.0.0")


# ---------------------------------------------------------------------------
# Pydantic models
# ---------------------------------------------------------------------------
class Student(BaseModel):
    name: str = Field(..., min_length=1, examples=["Asha"])
    subject: str = Field(..., examples=["Math"])
    score: int = Field(..., ge=0, le=100, examples=[88])


class StudentOut(Student):
    id: int


# ---------------------------------------------------------------------------
# In-memory "database" for demo purposes.
# ---------------------------------------------------------------------------
_db: dict[int, Student] = {
    1: Student(name="Asha", subject="Math", score=88),
    2: Student(name="Ravi", subject="English", score=82),
}
_next_id = 3


@app.get("/", tags=["health"])
def read_root() -> dict[str, str]:
    """Basic health-check route."""
    return {"status": "ok", "service": "mini-student-api"}


@app.get("/students", response_model=list[StudentOut], tags=["students"])
def list_students(
    subject: Optional[str] = Query(default=None, description="Filter by subject name"),
    min_score: int = Query(default=0, ge=0, le=100, description="Minimum score filter"),
) -> list[StudentOut]:
    """List students, optionally filtered by subject and/or minimum score (query params)."""
    results = [
        StudentOut(id=student_id, **student.model_dump())
        for student_id, student in _db.items()
        if (subject is None or student.subject.lower() == subject.lower())
        and student.score >= min_score
    ]
    return results


@app.get("/students/{student_id}", response_model=StudentOut, tags=["students"])
def get_student(student_id: int) -> StudentOut:
    """Fetch a single student by id (path parameter)."""
    student = _db.get(student_id)
    if student is None:
        raise HTTPException(status_code=404, detail=f"Student {student_id} not found")
    return StudentOut(id=student_id, **student.model_dump())


@app.post("/students", response_model=StudentOut, status_code=201, tags=["students"])
def create_student(student: Student) -> StudentOut:
    """Create a new student from a JSON body validated against the Student model."""
    global _next_id
    new_id = _next_id
    _db[new_id] = student
    _next_id += 1
    return StudentOut(id=new_id, **student.model_dump())


@app.delete("/students/{student_id}", status_code=204, tags=["students"])
def delete_student(student_id: int) -> None:
    """Delete a student by id."""
    if student_id not in _db:
        raise HTTPException(status_code=404, detail=f"Student {student_id} not found")
    del _db[student_id]
    return None


# There is no `if __name__ == "__main__":` block here on purpose --
# FastAPI apps are meant to be run through an ASGI server (uvicorn/hypercorn),
# not executed directly with `python this_file.py`.
