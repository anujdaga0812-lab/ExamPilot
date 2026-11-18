const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_student_1";
const BOB_UID = "bob_student_2";
const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validSubjectPayload(uid, now) {
  return {
    userId: uid,
    name: "Calculus II",
    examType: "SEMESTER",
    academicLevel: "UNIVERSITY",
    examDateEpochMillis: Date.now() + 86400000 * 10,
    syllabusText: "Integration by parts, Taylor series, polar coordinates",
    colorIndex: 0,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated user cannot read or write any ExamPilot collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const now = new Date(Date.now() - 1000);
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("subjects").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("subjects").doc("sub_1").set(validSubjectPayload(ALICE_UID, now))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("studyTasks").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("revisions").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("practiceTests").get());
});

test("Owner can create, list, update, and delete Subject; ghost fields & @DocumentId id are rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("subjects").doc("sub_1").set(validSubjectPayload(ALICE_UID, now))
  );

  // List matches repository observeSubjects query
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("subjects").get()
  );

  // Partial update preserving createdAt
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("subjects").doc("sub_1").update({
      name: "Calculus II Honors",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("subjects").doc("sub_2").set({
      ...validSubjectPayload(ALICE_UID, now),
      isAdmin: true,
    })
  );

  // Reject @DocumentId 'id' inside document body
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("subjects").doc("sub_3").set({
      id: "sub_3",
      ...validSubjectPayload(ALICE_UID, now),
    })
  );

  // Reject createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("subjects").doc("sub_1").update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("subjects").doc("sub_1").delete()
  );
});

test("Cross-user isolation prevents Bob from reading or modifying Alice's data", async () => {
  const now = new Date(Date.now() - 5000);
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    await ctx.firestore().collection("users").doc(ALICE_UID).collection("subjects").doc("sub_1").set(validSubjectPayload(ALICE_UID, now));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("subjects").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("subjects").doc("sub_1").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("subjects").doc("sub_1").update({
      name: "Hijacked",
      updatedAt: new Date(Date.now() - 1000),
    })
  );
});

test("StudyTask enforces parent subject existence and completion toggle", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  // Reject orphaned task when subject does not exist
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("studyTasks").doc("task_orphan").set({
      userId: ALICE_UID,
      subjectId: "missing_sub",
      subjectName: "Physics",
      topicTitle: "Kinematics",
      focusTip: "Review velocity graphs",
      scheduledDateIso: "2026-11-20",
      difficulty: "MEDIUM",
      estimatedMinutes: 45,
      completed: false,
      sortOrder: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("subjects").doc("sub_1").set(validSubjectPayload(ALICE_UID, now))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("studyTasks").doc("task_1").set({
      userId: ALICE_UID,
      subjectId: "sub_1",
      subjectName: "Calculus II",
      topicTitle: "Integration by parts",
      focusTip: "Practice 5 LIATE examples",
      scheduledDateIso: "2026-11-20",
      difficulty: "HARD",
      estimatedMinutes: 60,
      completed: false,
      sortOrder: 0,
      createdAt: now,
      updatedAt: now,
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("studyTasks").doc("task_1").update({
      completed: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("RevisionSheet and PracticeTest enforce schema and terminal state locking", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("subjects").doc("sub_1").set(validSubjectPayload(ALICE_UID, now))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("revisions").doc("rev_1").set({
      userId: ALICE_UID,
      subjectId: "sub_1",
      subjectName: "Calculus II",
      title: "Quick Revision: Integration",
      summary: "Summary of integration techniques.",
      keyPoints: ["Choose u using LIATE"],
      formulas: ["int(u dv) = uv - int(v du)"],
      examTips: ["Always add + C for indefinite integrals"],
      createdAt: now,
      updatedAt: now,
    })
  );

  const initialQuestions = [
    {
      questionId: "q1",
      type: "MCQ",
      prompt: "What does LIATE help choose?",
      options: ["u", "dv", "dx", "C"],
      correctAnswer: "u",
      maxMarks: 2,
      userAnswer: "",
      earnedMarks: 0,
      feedback: "",
      correct: false,
    },
  ];

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("practiceTests").doc("test_1").set({
      userId: ALICE_UID,
      subjectId: "sub_1",
      subjectName: "Calculus II",
      title: "Calculus II Practice",
      difficulty: "STANDARD",
      status: "IN_PROGRESS",
      totalMarks: 2,
      earnedMarks: 0,
      overallFeedback: "",
      questions: initialQuestions,
      createdAt: now,
      updatedAt: now,
    })
  );

  // Transition to GRADED
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("practiceTests").doc("test_1").update({
      status: "GRADED",
      earnedMarks: 2,
      overallFeedback: "Great job!",
      questions: [
        {
          ...initialQuestions[0],
          userAnswer: "u",
          earnedMarks: 2,
          feedback: "Spot on!",
          correct: true,
        },
      ],
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Terminal state lock: once GRADED, further updates must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("practiceTests").doc("test_1").update({
      earnedMarks: 0,
      updatedAt: new Date(Date.now() - 100),
    })
  );
});
